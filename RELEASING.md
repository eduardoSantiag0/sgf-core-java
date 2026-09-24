# Releasing to Maven Central

Everything Central requires (sources jar, javadoc jar, signatures, licence, SCM, developer) is wired
into the opt-in `release` profile, so ordinary builds never need GPG or credentials.

## One-time setup

None of it can be automated:

1. Create an account at <https://central.sonatype.com> and **verify the namespace**
   `io.github.eduardosantiag0` (for `io.github.<user>` this is done by creating a temporary public
   repository with the name the portal gives you).
2. Generate a portal **user token** and add it to `~/.m2/settings.xml`:
   ```xml
   <settings><servers><server>
     <id>central</id><username>TOKEN_USER</username><password>TOKEN_PASSWORD</password>
   </server></servers></settings>
   ```
3. Have a GPG key and publish its public part to a key server
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <ID>`).
4. Check `<url>` and `<scm>` in `pom.xml`: they assume the repository will be
   `https://github.com/eduardosantiag0/sgf-core-java`.

## Each release

```
# set a non-SNAPSHOT version (e.g. 0.1.0) in pom.xml, commit, then:
mvn -Prelease clean deploy
```

To check the release build without signing or publishing:

```
mvn -Prelease -Dgpg.skip=true clean verify
```
