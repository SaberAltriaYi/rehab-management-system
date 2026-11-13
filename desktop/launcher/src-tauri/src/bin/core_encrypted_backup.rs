fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let result = match args.as_slice() {
        [] => rehab_management_launcher::core_backup_headless::run(),
        [option] if option == "--verify-latest" =>
            rehab_management_launcher::core_backup_headless::verify_latest(),
        [option] if option == "--verify-restore" =>
            rehab_management_launcher::core_backup_headless::verify_restore(),
        _ => Err(())
    };
    if result.is_err() {
        // Never emit business data, passwords, CLI args or stdout from Docker.
        eprintln!("CORE_ENCRYPTED_BACKUP_FAILED");
        std::process::exit(1);
    }
    if args.is_empty() { println!("CORE_ENCRYPTED_BACKUP_CREATED"); }
    else if args[0] == "--verify-restore" {
        println!("CORE_ISOLATED_RESTORE_AND_P0_MIGRATIONS=PASS");
    } else { println!("CORE_ENCRYPTED_BACKUP_DECRYPTABLE=PASS"); }
}
