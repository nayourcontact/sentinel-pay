$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dir = Join-Path $root '.local\keycloak'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$realmFile = Join-Path $dir 'realm.json'
$credentialsFile = Join-Path $dir 'credentials.json'
$adminFile = Join-Path $dir 'admin.env'
if ((Test-Path $realmFile) -or (Test-Path $credentialsFile) -or (Test-Path $adminFile)) {
    throw 'Local Keycloak data exists; refusing to overwrite passwords. Remove .local/keycloak manually only if disposable.'
}
function New-Password {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$accounts = @(
    @{ username='reader'; role='PAYMENT_READER' },
    @{ username='operator'; role='PAYMENT_OPERATOR' },
    @{ username='agent-user'; role='AGENT_USER' },
    @{ username='ops-reader'; role='OPS_READER' },
    @{ username='no-role'; role=$null }
)
$passwords = @{}
$users = foreach ($account in $accounts) {
    $password = New-Password
    $passwords[$account.username] = $password
    $user = @{
        username = $account.username; enabled = $true
        firstName = 'Local'; lastName = 'Test'; email = "$($account.username)@example.invalid"
        emailVerified = $true; requiredActions = @()
        credentials = @(@{ type='password'; value=$password; temporary=$false })
    }
    if ($account.role) { $user.realmRoles = @($account.role) }
    $user
}
$realm = @{
    realm='sentinelpay'; enabled=$true; sslRequired='none'
    roles=@{ realm=@(
        'PAYMENT_READER','PAYMENT_OPERATOR','OPS_READER','AGENT_USER','KNOWLEDGE_WRITER','ADMIN' |
            ForEach-Object { @{ name=$_ } }
    ) }
    clients=@(@{
        clientId='sentinelpay-local-cli'; enabled=$true; protocol='openid-connect'
        publicClient=$true; directAccessGrantsEnabled=$true
        standardFlowEnabled=$false; serviceAccountsEnabled=$false
        protocolMappers=@(
            @{
                name='sentinelpay-roles'; protocol='openid-connect'; protocolMapper='oidc-usermodel-realm-role-mapper'
                consentRequired=$false
                config=@{
                    'multivalued'='true'; 'userinfo.token.claim'='false'
                    'id.token.claim'='false'; 'access.token.claim'='true'
                    'claim.name'='roles'; 'jsonType.label'='String'
                }
            },
            @{
                name='sentinelpay-audience'; protocol='openid-connect'; protocolMapper='oidc-audience-mapper'
                consentRequired=$false
                config=@{
                    'included.custom.audience'='sentinelpay-api'
                    'access.token.claim'='true'; 'id.token.claim'='false'
                }
            }
        )
    })
    users=@($users)
}
$adminPassword = New-Password
$realm | ConvertTo-Json -Depth 30 | Set-Content -Path $realmFile -Encoding UTF8
@{ users=$passwords; clientId='sentinelpay-local-cli'; realm='sentinelpay' } |
    ConvertTo-Json -Depth 10 | Set-Content -Path $credentialsFile -Encoding UTF8
@("KC_BOOTSTRAP_ADMIN_USERNAME=local-admin", "KC_BOOTSTRAP_ADMIN_PASSWORD=$adminPassword") |
    Set-Content -Path $adminFile -Encoding ASCII
Write-Host 'Generated local-only realm and credentials in .local/keycloak (never commit).'
Write-Host 'Credentials can be read from .local/keycloak/credentials.json locally.'
