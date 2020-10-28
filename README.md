# Hello Espresso

![](https://github.com/sbis04/hello_espresso/raw/master/screenshots/espresso_app.png)

Sample Android app with some instrumented tests using [Espresso](https://developer.android.com/training/testing/espresso). This also contains the **YAML** script and the **AWS** script for running Espresso tests on physical devices present in [AWS Device Farm](https://aws.amazon.com/device-farm/) using [Codemagic](https://codemagic.io/start/).

## Sample Codemagic YAML

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