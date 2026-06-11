# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

CUR_DIR=$(pwd)
SCRIPT_SOURCE_DIR=$(dirname "$0")
echo "Script source location"
echo "$SCRIPT_SOURCE_DIR"
cd $SCRIPT_SOURCE_DIR

# Required variables for entitlements tests
export TENANT_NAME="osdu"
export ENTITLEMENTS_DOMAIN="example.com"
export PARTITION_URL="${AWS_BASE_URL}/api/partition/v1/"
export INTEGRATION_TESTER_EMAIL="${ADMIN_USER}"
export DATA_ROOT_GROUP_HIERARCHY_ENABLED="true"
export LOCAL_MODE="false"

# Authentication setup
export PRIVILEGED_USER_TOKEN=$(curl --location ${TEST_OPENID_PROVIDER_URL} --header "Content-Type:application/x-www-form-urlencoded" --header "Authorization:Basic ${SERVICE_PRINCIPAL_AUTHORIZATION}" --data-urlencode "grant_type=client_credentials" --data-urlencode ${IDP_ALLOWED_SCOPES} --http1.1 | jq -r '.access_token')
export ROOT_USER_TOKEN=$PRIVILEGED_USER_TOKEN
export NO_ACCESS_USER_TOKEN=$(aws cognito-idp initiate-auth --region ${AWS_REGION} --auth-flow ${AWS_COGNITO_AUTH_FLOW} --client-id ${AWS_COGNITO_CLIENT_ID} --auth-parameters "{\"USERNAME\":\"${USER_NO_ACCESS}\",\"PASSWORD\":\"${USER_NO_ACCESS_PASSWORD}\"}" --query AuthenticationResult.AccessToken --output text)


mvn clean test
TEST_EXIT_CODE=$?

cd $CUR_DIR

if [ -n "$1" ]; then
    mkdir -p "$1"
    mkdir -p $1/os-entitlements
    cp -R $SCRIPT_SOURCE_DIR/target/surefire-reports/* $1/os-entitlements
fi

exit $TEST_EXIT_CODE

