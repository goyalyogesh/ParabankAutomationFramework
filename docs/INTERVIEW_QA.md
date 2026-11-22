# Interview Q&A — explain the framework, then survive the "what if" questions

Format: **Q** → short answer you can say out loud. "Tweak" questions change the scenario.

## Configuration

**Q: How does your framework handle different settings for local runs and Jenkins?**
A: `ConfigReader` with precedence file < environment variable < `-D` system property. Defaults live in
`config.properties`; Jenkins passes `-Dheadless=true -Dthreads=5`. No code or file change needed.

**Tweak: Tomorrow you must run against QA, UAT and PROD-like environments. What changes?**
A: Add `env` key (`-Denv=uat`) and load `config-${env}.properties` on top of the base file. Same precedence
chain; only the file name is chosen dynamically.

**Tweak: A password must not be in the properties file. How?**
A: Don't put it there. Read it from an environment variable that Jenkins fills from its Credentials store
(`withCredentials`). `ConfigReader` already reads env vars, so no code change.

**Q: Why return `Optional` from `find`?**
A: It forces the caller to decide what happens when the value is missing (default or error) instead of
getting a `NullPointerException` far away from the cause.
