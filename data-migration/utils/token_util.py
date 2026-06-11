import msal
import urllib


def generate_service_principal_token(service_principal_id, service_principal_secret, azure_tenant_id, resource_id):
    authority_host_uri = 'https://login.microsoftonline.com'
    authority_uri = urllib.parse.urljoin(authority_host_uri, azure_tenant_id)
    app = msal.ConfidentialClientApplication(service_principal_id, authority=authority_uri,
                                             client_credential=service_principal_secret)
    token_response = app.acquire_token_for_client(scopes=[resource_id + '/.default'])

    if "access_token" in token_response:
        return token_response.get('access_token')
    else:
        raise Exception('error generating service principal token {}'.format(token_response))
