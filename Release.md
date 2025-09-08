Releases
--------

The process for creating a new release for fjage is as follows:

1. Update the version number in the `VERSION` file.
2. Make sure that the version number is also updated in this `README.md` file.
3. Update the `ReleaseNotes.md` file with the changes made since the last release.
4. Commit the changes and push to the `main` branch.
5. Create a new git tag with the version number (e.g., `v2.0.1`).
6. Push the changes and the tag to GitHub.
7. Publish the release on GitHub using `./gradlew publish` (requires credentials).
