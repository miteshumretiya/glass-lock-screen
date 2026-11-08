`release.jks.enc` is the app's release signing key, encrypted with AES-256.
The build decrypts it with the `SIGNING_PASSWORD` repository secret, so every
release is signed with the same key and installs as an update over the last one.
Without that secret the build falls back to a throwaway debug key.
