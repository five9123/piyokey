# Verification artifacts

Milestone and release README files remain in the source repository as the test
and provenance record. Screenshots, recordings, Instruments traces, Xcode test
results, archives, and exported binaries are stored outside the source
repository according to `docs/REPOSITORY_POLICY.md`.

Release-critical external artifacts must be referenced by a text manifest that
records their version, SHA-256 checksum, provenance, and retrieval location.
Local artifacts under this directory are ignored and can be regenerated or
restored from the private release-assets archive.
