use super::*;
use tempfile::tempdir;

fn signed<T: Serialize>(protocol: &str, value: &T, issuer: &Ed25519KeyPair) -> String {
    let json = URL_SAFE_NO_PAD.encode(serde_json::to_vec(value).unwrap());
    let prefix = format!("{protocol}.synthetic-owner.{json}");
    format!("{}.{}", prefix,
        URL_SAFE_NO_PAD.encode(issuer.sign(prefix.as_bytes()).as_ref()))
}
fn timestamp(ms: i64) -> String {
    DateTime::<Utc>::from_timestamp_millis(ms).unwrap()
        .to_rfc3339_opts(SecondsFormat::Millis, true)
}
fn fixture() -> (tempfile::TempDir, AppPaths, LicenseManager, Ed25519KeyPair) {
    let folder = tempdir().unwrap();
    let rng = SystemRandom::new();
    let owner = Ed25519KeyPair::from_pkcs8(
        Ed25519KeyPair::generate_pkcs8(&rng).unwrap().as_ref()).unwrap();
    let resource = folder.path().join("resources");
    fs::create_dir_all(resource.join("license")).unwrap();
    fs::write(resource.join("license/trusted-issuers.json"),
        serde_json::to_vec(&serde_json::json!({
            "ownerBaseUrl": "https://owner.example.invalid/",
            "issuers": [{
                "kid": "synthetic-owner",
                "publicKeyBase64url": URL_SAFE_NO_PAD.encode(owner.public_key().as_ref())
            }]
        })).unwrap()).unwrap();
    let paths = AppPaths::from_data_dir(folder.path().join("data"));
    paths.create().unwrap();
    let manager = LicenseManager::new(&paths, &resource);
    (folder, paths, manager, owner)
}
fn signed_file(owner: &Ed25519KeyPair, id: &LocalInstallation,
               status: &str, epoch: u64, expires: i64) -> SignedActivationFile {
    let t = now();
    let lease = LeaseClaims {
        schema_version: 1, mode: "offline".into(), lease_id: Uuid::new_v4().to_string(),
        tenant_id: id.tenant_id.clone(), installation_id: id.installation_id.clone(),
        issued_at: timestamp(t - 5_000), not_before: timestamp(t - 5_000),
        expires_at: timestamp(expires), entitlement_expires_at: timestamp(t + MAX_OFFLINE_MS),
        revocation_epoch: epoch, seat_limit: Some(6), max_installations: 1,
        modules: vec!["motion".into(), "rehab".into(), "report".into()]
    };
    let status = StatusClaims {
        schema_version: 1, tenant_id: id.tenant_id.clone(),
        installation_id: id.installation_id.clone(), status: status.into(),
        revocation_epoch: epoch, observed_at: timestamp(t - 3_000),
        expires_at: timestamp(t + MAX_OFFLINE_MS - 3_000)
    };
    SignedActivationFile {
        schema_version: 1, tenant_id: id.tenant_id.clone(),
        installation_id: id.installation_id.clone(),
        token: signed("RHL1", &lease, owner),
        status_proof: signed("RHR1", &status, owner)
    }
}

#[test]
fn key_generation_creates_unique_identity_and_never_resets_without_owner() {
    let (_dir, paths, manager, _) = fixture();
    let id = manager.begin_application("合成工作室".into()).unwrap();
    assert!(exact_uuid(&id.tenant_id) && exact_uuid(&id.installation_id));
    assert_eq!(manager.overview().status, "PENDING");
    assert!(manager.begin_application("第二个工作室".into()).is_err());
    let pem = manager.public_key_pem().unwrap();
    assert!(pem.starts_with("-----BEGIN PUBLIC KEY-----"));
    assert!(!pem.contains("PRIVATE"));
    #[cfg(unix)] {
        use std::os::unix::fs::PermissionsExt;
        assert_eq!(fs::metadata(paths.secrets_dir.join("license/installation.pk8"))
            .unwrap().permissions().mode() & 0o777, 0o600);
    }
}

#[test]
fn signed_offline_license_activates_persists_and_prepares_java_handoff() {
    let (dir, paths, manager, owner) = fixture();
    let id = manager.begin_application("合成测试康复工作室".into()).unwrap();
    let signed = signed_file(&owner, &id, "ACTIVE", 1, now() + 3_600_000);
    let bytes = serde_json::to_vec(&signed).unwrap();
    let result = manager.import_offline_bytes(&bytes).unwrap();
    assert_eq!(result.status, "GRACE");
    assert_eq!(result.seat_limit, Some(6));
    manager.prepare_runtime_handoff().unwrap();
    let handoff = fs::read(paths.config_dir.join("license-runtime/activation.json")).unwrap();
    let from_file: SignedActivationFile = serde_json::from_slice(&handoff).unwrap();
    assert_eq!(from_file.status_proof, signed.status_proof);
    assert!(!String::from_utf8_lossy(&handoff).contains("PRIVATE KEY"));
    let restarted = LicenseManager::new(&paths, &dir.path().join("resources"));
    assert!(restarted.is_active());
}

#[test]
fn mismatched_tenant_or_installation_and_tampered_signature_fail_closed() {
    let (_dir, _, manager, owner) = fixture();
    let id = manager.begin_application("合成工作室".into()).unwrap();
    let mut file = signed_file(&owner, &id, "ACTIVE", 0, now() + 60_000);
    file.tenant_id = Uuid::new_v4().to_string();
    assert!(manager.import_offline_bytes(&serde_json::to_vec(&file).unwrap()).is_err());
    file.tenant_id = id.tenant_id;
    file.installation_id = Uuid::new_v4().to_string();
    assert!(manager.import_offline_bytes(&serde_json::to_vec(&file).unwrap()).is_err());
    file.installation_id = id.installation_id;
    file.token.push('z');
    assert!(manager.import_offline_bytes(&serde_json::to_vec(&file).unwrap()).is_err());
    assert_eq!(manager.overview().status, "PENDING");
}

#[test]
fn revoked_expired_and_72_hour_exceeding_files_do_not_activate() {
    let (_dir, _, manager, owner) = fixture();
    let id = manager.begin_application("合成工作室".into()).unwrap();
    for file in [
        signed_file(&owner, &id, "REVOKED", 10, now() + 60000),
        signed_file(&owner, &id, "ACTIVE", 1, now() - 1000),
        signed_file(&owner, &id, "ACTIVE", 1, now() + MAX_OFFLINE_MS + 60000)
    ] {
        assert!(manager.import_offline_bytes(&serde_json::to_vec(&file).unwrap()).is_err());
    }
    assert!(!manager.is_active());
}

#[test]
fn locally_persisted_epoch_and_clock_cannot_be_decreased_by_old_license() {
    let (_dir, paths, manager, owner) = fixture();
    let id = manager.begin_application("合成工作室".into()).unwrap();
    let old = signed_file(&owner, &id, "ACTIVE", 1, now() + 3600_000);
    manager.import_offline_bytes(&serde_json::to_vec(&old).unwrap()).unwrap();
    let mut record = manager.record().unwrap();
    record.revocation_epoch = 5;
    manager.store_record(&record).unwrap();
    assert!(manager.import_offline_bytes(&serde_json::to_vec(&old).unwrap()).is_err());
    record.trusted_time_ms = now() + 10 * 60_000;
    manager.store_record(&record).unwrap();
    assert!(!manager.is_active());
    assert_eq!(manager.overview().status, "INVALID");
    assert!(paths.config_dir.join("license/record.json").exists());
}

#[test]
fn replacement_creates_distinct_key_request_but_never_self_approves() {
    let (_dir, _, manager, _) = fixture();
    let request = manager.prepare_replacement(
        Uuid::new_v4().to_string(), Uuid::new_v4().to_string(),
        "新设备工作室".into()).unwrap();
    assert!(request.contains("newPublicKeyPem"));
    assert!(request.contains("proof"));
    assert_eq!(manager.overview().status, "PENDING");
    assert!(manager.submit_application().is_err());
    assert!(!manager.is_active());
}

#[test]
fn empty_bundled_trust_never_creates_a_forged_owner_identity() {
    let dir = tempdir().unwrap();
    let paths = AppPaths::from_data_dir(dir.path().join("data"));
    paths.create().unwrap();
    fs::create_dir_all(dir.path().join("resources/license")).unwrap();
    fs::write(dir.path().join("resources/license/trusted-issuers.json"),
        br#"{"ownerBaseUrl":null,"issuers":[]}"#).unwrap();
    let manager = LicenseManager::new(&paths, &dir.path().join("resources"));
    assert!(!manager.overview().publisher_configured);
    assert_eq!(manager.overview().status, "UNLICENSED");
    assert!(manager.begin_application("未发行工作室".into()).is_err());
}

#[test]
fn l5_node_owner_signed_tokens_interoperate_with_rust_real_issuer() {
    // Real OwnerLicensingService supplies this short-lived synthetic fixture.
    // Plain cargo test without the fixture does NOT constitute an L5 pass.
    let path = match std::env::var("REHAB_L5_NODE_FIXTURE") {
        Ok(path) => path,
        Err(_) => return,
    };
    let bytes = fs::read(path).unwrap();
    assert!(bytes.len() < 30_000);
    let fixture: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(fixture["protocol"], "rehab-l5-node-cross-runtime/1");
    let kid = fixture["issuerKid"].as_str().unwrap().to_owned();
    let raw = URL_SAFE_NO_PAD.decode(
        fixture["publicKeyBase64url"].as_str().unwrap()).unwrap();
    assert_eq!(raw.len(), 32);
    let owner = TrustedOwner { endpoint: None, keys: [(kid, raw)].into_iter().collect() };
    let t = fixture["nowMs"].as_i64().unwrap();
    let studios = fixture["studios"].as_array().unwrap();
    assert_eq!(studios.len(), 2);
    for (i, site) in studios.iter().enumerate() {
        let tenant = site["tenantId"].as_str().unwrap();
        let installation = site["installationId"].as_str().unwrap();
        let token = site["token"].as_str().unwrap();
        let status = site["statusProof"].as_str().unwrap();
        let signed_status = verify_status(status, &owner, tenant, installation, t, t).unwrap();
        assert_eq!(signed_status.status, "ACTIVE");
        let lease = verify_lease(token, &owner, tenant, installation, t, 0, t).unwrap();
        assert_eq!(lease.mode, "online");
        assert_eq!(lease.seat_limit, if i == 0 { Some(2) } else { None });
        assert!(verify_status(status, &owner,
            studios[1 - i]["tenantId"].as_str().unwrap(), installation, t, t).is_err());
        assert!(verify_lease(token, &owner,
            studios[1 - i]["tenantId"].as_str().unwrap(), installation, t, 0, t).is_err());
        assert!(verify_lease(token, &owner, tenant, installation, t, 1, t).is_err());
        assert!(verify_lease(token, &owner, tenant, installation, t + MAX_OFFLINE_MS, 0, t).is_err());
        assert!(verify_lease(token, &owner, tenant, installation, t, 0, t + 600_000).is_err());
    }
    let b = &studios[1];
    let revoked = verify_status(fixture["revokedStatusProof"].as_str().unwrap(),
        &owner, b["tenantId"].as_str().unwrap(), b["installationId"].as_str().unwrap(),
        t + 1000, t).unwrap();
    assert_eq!(revoked.status, "REVOKED");
    assert_eq!(revoked.revocation_epoch, 1);
}
