fn main() {
    if let Err(_error) = rehab_management_launcher::owner_recovery_headless::run() {
        // No passwords, challenges, SQL, server diagnostics or Owner
        // authorization secrets are ever printed on failure.
        eprintln!("OWNER_CORE_RECOVERY_FAILED");
        std::process::exit(1);
    }
}
