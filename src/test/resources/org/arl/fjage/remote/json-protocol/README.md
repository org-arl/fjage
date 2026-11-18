# JSON protocol baseline fixtures

These files record what master's encoder sends, captured on Java 8. `JsonProtocolFixtures.samples()` defines the inputs, with fixed message IDs, envelope IDs, dates and timestamps; each sample's name is its file name. Test-only message class names are part of the fixtures.

- The first 11 files were captured from master `d16f8455`.
- `nested-pojo`, `contains-agent-request` and `agent-for-service-response` were captured from master `d3f91d6b`. Nothing in the codec changed between the two commits, and the first 11 files are byte-identical on both.

`JsonProtocolCompatibilityTest` compares the encoder's output with these files. It checks member order exactly for typed messages, and sorts object members where map iteration order isn't part of the contract. The sorted comparison keeps numbers as they are spelled. Separate checks cover escaped strings, Base64 data, `clazz` before `data` in every wrapper, and signed zero. Decoding checks cover headers, typed values, every primitive array type, and parameter requests and responses.

The non-finite fixtures contain bare `NaN` and `Infinity` tokens. These are not standard JSON, but they are what master sends, and Gson's lenient parser reads them. Don't reformat these files with a strict JSON tool.

Some fixtures record known bugs as they are today. For example, `nested-message` and `nested-pojo` drop the null fields that follow a nested message, and the two envelope fixtures send `"message":null`.

## Approved differences

Never regenerate the baseline files with a changed encoder. When a change is approved to alter the output, add the expected output under `corrected/`, using the baseline's file name. The test uses the `corrected/` file in place of the baseline. List each approved difference below.

None yet.

## Running

`./gradlew test --tests 'org.arl.fjage.remote.JsonProtocolCompatibilityTest' -x buildjs -x setupPyVenv --no-daemon`

The benchmark, `JsonCodecBenchmark`, is a manual harness that JUnit doesn't run. Run its `main` on the test runtime classpath. It prints one CSV row per case: nanoseconds per operation, bytes allocated per operation, and the iteration count.
