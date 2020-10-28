# Hello Espresso

![](https://github.com/sbis04/hello_espresso/raw/master/screenshots/espresso_app.png)

Sample Android app with some instrumented tests using [Espresso](https://developer.android.com/training/testing/espresso). This also contains the **YAML** script and the **AWS** script for running Espresso tests on physical devices present in [AWS Device Farm](https://aws.amazon.com/device-farm/) using [Codemagic](https://codemagic.io/start/).

## Codemagic YAML template

You can use the following template for running tests on AWS Device Farm using Codemagic.

> Check and modify the file as per your credentials and build pipeline, before using it on Codemagic.

```yaml
# Check out https://docs.codemagic.io/getting-started/yaml/for more information

workflows:
    native-android:
        name: Android Espresso
        environment:
            vars:
                # AWS Device Farm Environment Variables
                # See more information from official AWS Device Farm docs:
                # https://docs.aws.amazon.com/devicefarm/latest/developerguide/how-to-create-test-run.html#how-to-create-test-run-cli
                AWS_ACCESS_KEY_ID: Encrypted(...) # <-- Put your encrypted access key id here. See more info at https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html#access-keys-and-secret-access-keys
                AWS_SECRET_ACCESS_KEY: Encrypted(...) # <-- Put your encrypted secret access key here. See more info at https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html#access-keys-and-secret-access-keys
                AWS_PROJECT_ARN: Encrypted(...) # <-- Put your encrypted Project ARN here. Get Project ARN with `aws devicefarm list-projects`.
                AWS_DEVICE_POOL_ARN: Encrypted(...) # <-- Put your encrypted Device Pool ARN here. Choose a Device Pool ARN using `aws devicefarm list-device-pools --arn "${AWS_PROJECT_ARN}"`.
                AWS_DEFAULT_REGION: "us-west-2" # See available options at https://aws.amazon.com/about-aws/global-infrastructure/regional-product-services/
                # Set up paths to application binary and test package 
                AWS_APK_PATH: "$FCI_BUILD_DIR/app/build/outputs/apk/debug/app-debug.apk"
                AWS_TEST_APK_PATH: "$FCI_BUILD_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
                # Specify application and test types
                AWS_APP_TYPE: "ANDROID_APP" # Type of the main app that is being tested. See more about `--type` flag at https://docs.aws.amazon.com/cli/latest/reference/devicefarm/create-upload.html
                AWS_TEST_PACKAGE_TYPE: "INSTRUMENTATION_TEST_PACKAGE" # Type of the test package that is being uploaded. See more about `--type` flag at https://docs.aws.amazon.com/cli/latest/reference/devicefarm/create-upload.html
                AWS_TEST_TYPE: "INSTRUMENTATION" # See more about `--test` flag `type` option at https://docs.aws.amazon.com/cli/latest/reference/devicefarm/schedule-run.html
        scripts:
            - name: AWS CLI configuration
              script: |
                mkdir ~/.aws
                cat >> ~/.aws/config <<EOF
                [default]
                aws_access_key_id=$AWS_ACCESS_KEY_ID
                aws_secret_access_key=$AWS_SECRET_ACCESS_KEY
                region=$AWS_DEFAULT_REGION
                output=json
                EOF
            - name: Set Android SDK location
              script: |
                echo "sdk.dir=$HOME/programs/android-sdk-macosx" > "$FCI_BUILD_DIR/local.properties"
            - name: Generate debug keystore
              script: |
                rm -f ~/.android/debug.keystore
                keytool -genkeypair \
                  -alias androiddebugkey \
                  -keypass android \
                  -keystore $FCI_BUILD_DIR/app/debug.keystore \
                  -storepass android \
                  -dname 'CN=Android Debug,O=Android,C=US' \
                  -keyalg 'RSA' \
                  -keysize 2048 \
                  -validity 10000
            - name: Build Android debug APK
              script: |
                ./gradlew assembleDebug
            - name: Build Android test APK
              script: |
                ./gradlew assembleAndroidTest
            - name: Run AWS Device Farm test
              script: |
                cd .scripts && ./run_aws_espresso.sh
            - name: Verify Device Farm test
              script: |
                set -e
                set -x
                export AWS_RESULT=$(cat $FCI_BUILD_DIR/.scripts/test-result.json | jq -r '.run.result')
                if [ $AWS_RESULT != "PASSED" ] 
                then
                  echo "AWS tests did not pass, the result was $AWS_RESULT"
                  exit 1
                else 
                  echo "AWS tests PASSED!"
                fi 
        artifacts:
            - app/build/outputs/**/*.apk
        publishing:
            email:
                recipients:
                    - user1@example.com
```

## AWS Device Farm script template

Create a file called `run_aws_espresso.sh` inside a folder `.scripts`, and place it in the root project directory. Add the following to the file.

> Modify as per your tests

```sh
#!/usr/bin/env zsh

set -e

upload_package() {
  PACKAGE_PATH="${1}"
  UPLOAD_INFO="${2}"

  echo "${UPLOAD_INFO}" | jq
  UPLOAD_URL=$(echo "${UPLOAD_INFO}" | jq -r '.upload.url')
  UPLOAD_ARN=$(echo "${UPLOAD_INFO}" | jq -r '.upload.arn')
  curl -T "${PACKAGE_PATH}" "${UPLOAD_URL}"

  while true; do
    UPLOAD_RESULT=$(aws devicefarm get-upload --arn "${UPLOAD_ARN}")
    UPLOAD_STATUS=$(echo "${UPLOAD_RESULT}" | jq -r '.upload.status')

    if [ "${UPLOAD_STATUS}" = "FAILED" ]; then
      echo "Upload did not complete successfully, the status was ${UPLOAD_STATUS}"
      echo "Unable to proceed with the tests"
      exit 1
    elif [ "${UPLOAD_STATUS}" != "SUCCEEDED" ]; then
      echo "Upload of ${PACKAGE_PATH} is not completed, current status is ${UPLOAD_STATUS}"
      echo "Wait until upload is completed ..."
      sleep 5
    else
      echo "Uploading ${PACKAGE_PATH} is completed with status ${UPLOAD_STATUS}"
      break
    fi
  done
}

wait_for_test_results() {
  SCHEDULED_TEST_RUN="${1}"
  TEST_RUN_ARN=$(echo "${SCHEDULED_TEST_RUN}" | jq -r '.run.arn')
  echo "${SCHEDULED_TEST_RUN}" | jq

  while true;
  do
    TEST_RUN=$(aws devicefarm get-run --arn "${TEST_RUN_ARN}")
    TEST_RUN_STATUS=$(echo "$TEST_RUN" | jq -r '.run.status')
    if [ "${TEST_RUN_STATUS}" != "COMPLETED" ]; then
      echo "Test run is not completed, current status is ${TEST_RUN_STATUS}"
      sleep 30
    else
      break
    fi
  done

  echo "${TEST_RUN}" | jq
  echo "${TEST_RUN}" > test-result.json
  echo "Test run completed, saving result to test-result.json"
}

# Upload Your Application File
echo "Upload ${AWS_APK_PATH} to Device Farm"
APP_UPLOAD_INFO=$(aws devicefarm create-upload \
  --project-arn "${AWS_PROJECT_ARN}" \
  --name "$(basename "${AWS_APK_PATH}")" \
  --type "${AWS_APP_TYPE}")
APP_UPLOAD_ARN=$(echo "${APP_UPLOAD_INFO}" | jq -r '.upload.arn')
upload_package "${AWS_APK_PATH}" "${APP_UPLOAD_INFO}"

# Upload Your Test Scripts Package
echo "Upload ${AWS_TEST_APK_PATH} to Device Farm"
TEST_UPLOAD_INFO=$(aws devicefarm create-upload \
  --project-arn "${AWS_PROJECT_ARN}" \
  --name "$(basename "${AWS_TEST_APK_PATH}")" \
  --type "${AWS_TEST_PACKAGE_TYPE}")
TEST_UPLOAD_ARN=$(echo "${TEST_UPLOAD_INFO}" | jq -r '.upload.arn')
upload_package "${AWS_TEST_APK_PATH}" "${TEST_UPLOAD_INFO}"

# Schedule a Test Run
echo "Schedule test run for uploaded app and tests package"
SCHEDULED_TEST_RUN_INFO=$(aws devicefarm schedule-run \
  --project-arn "${AWS_PROJECT_ARN}" \
  --app-arn "${APP_UPLOAD_ARN}" \
  --device-pool-arn "${AWS_DEVICE_POOL_ARN}" \
  --name "CM test run for build" \
  --test type=${AWS_TEST_TYPE},testPackageArn="${TEST_UPLOAD_ARN}")

wait_for_test_results "${SCHEDULED_TEST_RUN_INFO}"
```

## License

Copyright (c) 2020 Souvik Biswas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
