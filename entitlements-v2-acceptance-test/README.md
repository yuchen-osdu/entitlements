### Running E2E Tests

These tests use the shared `os-core-test` library. HTTP endpoints, authentication and the data
partition are resolved from the environment variables below (loaded by `os-core-test`'s
`EnvLoader` / `ServicesConfig` / token providers).

| name                                | value                                              | description                                                                                         | sensitive? | source | required |
|-------------------------------------|----------------------------------------------------|-----------------------------------------------------------------------------------------------------|------------|--------|----------|
| `HOST`                              | ex `https://osdu.example.com`                      | Base host for all OSDU services; the entitlements URL is `HOST` + `/api/entitlements/v2/`            | no         | -      | yes      |
| `DATA_PARTITION_ID`                 | ex `opendes`                                       | OSDU data partition used for testing                                                                | no         | -      | yes      |
| `ENTITLEMENTS_DOMAIN`               | ex `contoso.com`                                   | Entitlements group domain used to build well-known group emails (defaults to `group`)               | no         | -      | no       |
| `INDEXER_SERVICE_ACCOUNT_EMAIL`     | ex `workload-indexer@osdu.iam.gserviceaccount.com` | Indexer service account email with special privileges for data groups (`GetDataGroupsIndexer...`)   | no         | -      | no       |
| `DATA_ROOT_GROUP_HIERARCHY_ENABLED` | ex `true`                                          | Controls whether data.root groups get access to all data groups (depends on partition feature flag) | no         | -      | no       |

Authentication is provided per user type. Tests use `PRIVILEGED_USER` and `NO_ACCESS_USER`.
It can be supplied as OIDC client credentials:

| name                                            | value                                      | description                                 | sensitive? | source |
|-------------------------------------------------|--------------------------------------------|---------------------------------------------|------------|--------|
| `TEST_OPENID_PROVIDER_URL`                      | ex `https://keycloak.com/auth/realms/osdu` | OpenID provider url                         | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_ID`     | `********`                                 | Privileged User Client Id                   | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_SECRET` | `********`                                 | Privileged User Client secret               | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_SCOPE`         | ex `api://my-app/.default`                 | OAuth2 scope (optional, defaults to openid) | no         | -      |
| `NO_ACCESS_USER_OPENID_PROVIDER_CLIENT_ID`      | `********`                                 | No-access User Client Id                    | yes        | -      |
| `NO_ACCESS_USER_OPENID_PROVIDER_CLIENT_SECRET`  | `********`                                 | No-access User Client secret                | yes        | -      |

Or as pre-issued bearer tokens (`{USER_TYPE}_TOKEN`), which take precedence over OIDC config:

| name                    | value      | description           | sensitive? | source |
|-------------------------|------------|-----------------------|------------|--------|
| `PRIVILEGED_USER_TOKEN` | `********` | Privileged User Token | yes        | -      |
| `NO_ACCESS_USER_TOKEN`  | `********` | No-access User Token  | yes        | -      |

**Entitlements configuration for integration accounts**

| INTEGRATION_TESTER         | NO_DATA_ACCESS_TESTER     |
|----------------------------|---------------------------|
| users                      | users                     |
| service.entitlements.user  | service.entitlements.user |
| service.entitlements.admin | service.storage.admin     |
| users.datalake.delegation  |                           |

Execute following command to build code and run all the integration tests:

 ```bash
 # Note: this assumes that the environment variables for integration tests as outlined
 #       above are already exported in your environment.
 # build + install integration test core
 $ (cd entitlements-v2-acceptance-test && mvn clean verify)
 ```

## License

Copyright © Google LLC

Copyright © EPAM Systems

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
