from azure.identity import DefaultAzureCredential
from azure.keyvault.secrets import SecretClient


def get_secret(kv_uri, secret_name):
    credential = DefaultAzureCredential()
    client = SecretClient(vault_url=kv_uri, credential=credential)
    secret = client.get_secret(secret_name)
    return secret.value
