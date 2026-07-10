Releases
--------

The process for creating a new release for fjage is as follows:

1. Update the version number in the `VERSION` file.
2. Make sure that the version number is also updated in this `README.md` file.
3. Update the version number in the documentation: `docs/_variables.yml`, and the hard-coded versions in `docs/quickstart.qmd` (directory listing), `docs/introduction.qmd` (Maven snippet) and `docs/fjage_quickstart.sh`.
4. Update the `ReleaseNotes.md` file with the changes made since the last release.
5. Commit the changes and push to the `master` branch.
6. Create a new git tag with the version number (e.g., `v2.0.1`).
7. Push the changes and the tag to GitHub.
8. Publish the release on GitHub using `./gradlew publish` (requires credentials).
