#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$AdjustDemoAppointmentClock
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$BaseUrl = $BaseUrl.TrimEnd('/')
if ($AdjustDemoAppointmentClock -and -not ([uri]$BaseUrl).IsLoopback) {
    throw 'El ajuste de demostración requiere una API local.'
}
function New-DemoPassword {
    [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
}
function Invoke-DemoRequest([string]$Verb, [string]$Route, $Payload, $Headers, [int]$Expected) {
    $requestOptions = @{ Uri="$BaseUrl$Route"; Method=$Verb; SkipHttpErrorCheck=$true }
    if ($null -ne $Headers) { $requestOptions.Headers = $Headers }
    if ($null -ne $Payload) {
        $requestOptions.ContentType = 'application/json'
        $requestOptions.Body = ConvertTo-Json -InputObject $Payload -Depth 10 -Compress
    }
    $response = Invoke-WebRequest @requestOptions
    if ([int]$response.StatusCode -ne $Expected) {
        throw "HTTP inesperado en $Verb $Route; esperado $Expected, recibido $($response.StatusCode)."
    }
    if ($Expected -ge 400 -or [string]::IsNullOrWhiteSpace($response.Content)) { return $null }
    if ($response.Headers['Content-Type'] -like '*text/html*') { return $null }
    ConvertFrom-Json -InputObject $response.Content
}
function Get-DemoSession([string]$Email, [string]$Password) {
    $session = Invoke-DemoRequest 'POST' '/api/v1/auth/login' @{email=$Email; password=$Password} $null 200
    @{ Authorization="Bearer $($session.accessToken)" }
}
function Wait-DemoUntil([DateTimeOffset]$When) {
    while ([DateTimeOffset]::UtcNow -lt $When) {
        $remaining = ($When - [DateTimeOffset]::UtcNow).TotalSeconds
        Start-Sleep -Seconds ([Math]::Max(1,[Math]::Min(30,[Math]::Ceiling($remaining))))
    }
}
function Set-DemoAppointmentPast([guid]$AppointmentId, [guid]$ClientId, [guid]$BarberId) {
    if ($env:DB_URL -notmatch '^jdbc:postgresql://([^:/?#]+):(\d+)/barberia_demo$') {
        throw 'El ajuste exige DB_URL con host y puerto explícitos y base barberia_demo.'
    }
    $demoHost = $Matches[1]; $demoPort = $Matches[2]
    if (-not $env:DB_USERNAME -or -not $env:DB_PASSWORD) { throw 'Faltan variables PostgreSQL del entorno.' }
    $psqlExecutable = if ($env:PSQL_PATH) { $env:PSQL_PATH } else { (Get-Command psql -ErrorAction Stop).Source }
    $savedPgEnvironment = @{}
    foreach ($variableName in @('PGHOST','PGPORT','PGDATABASE','PGUSER','PGPASSWORD')) {
        $savedPgEnvironment[$variableName] = [Environment]::GetEnvironmentVariable($variableName)
    }
    try {
        $env:PGHOST=$demoHost; $env:PGPORT=$demoPort; $env:PGDATABASE='barberia_demo'
        $env:PGUSER=$env:DB_USERNAME; $env:PGPASSWORD=$env:DB_PASSWORD
        $databaseName = & $psqlExecutable -X -w -tA -v ON_ERROR_STOP=1 -c 'select current_database()'
        if ($LASTEXITCODE -ne 0 -or "$databaseName".Trim() -ne 'barberia_demo') { throw 'Base de demostración no confirmada.' }
        # Only the appointment just created by this run is changed. No Clock override,
        # status, price, account, stock, payment or existing appointment is modified.
        $demoSql = @'
UPDATE appointments
SET starts_at = (date_trunc('hour', now() AT TIME ZONE 'America/Mexico_City') AT TIME ZONE 'America/Mexico_City') - interval '2 hours',
    ends_at = (date_trunc('hour', now() AT TIME ZONE 'America/Mexico_City') AT TIME ZONE 'America/Mexico_City') - interval '1 hour'
WHERE id = :'appointment_id'::uuid AND client_id = :'client_id'::uuid
  AND barber_id = :'barber_id'::uuid AND status = 'CONFIRMED'
RETURNING id;
'@
        # psql variable interpolation is supported for stdin scripts, not -c SQL strings.
        $adjusted = $demoSql | & $psqlExecutable -X -w -tA -v ON_ERROR_STOP=1 -v appointment_id=$AppointmentId -v client_id=$ClientId -v barber_id=$BarberId
        if ($LASTEXITCODE -ne 0 -or -not ($adjusted -contains $AppointmentId.ToString())) { throw 'No se ajustó la cita de esta demostración.' }
    } finally {
        foreach ($variableName in $savedPgEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($variableName,$savedPgEnvironment[$variableName],'Process')
        }
    }
}

$adminEmail = if ($env:DEMO_ADMIN_EMAIL) { $env:DEMO_ADMIN_EMAIL } else { Read-Host 'Correo del ADMIN inicializado' }
$adminPassword = $env:DEMO_ADMIN_PASSWORD
if (-not $adminPassword) {
    $adminSecret = Read-Host 'Contraseña de ADMIN' -AsSecureString
    $adminPassword = [Net.NetworkCredential]::new('', $adminSecret).Password
    Remove-Variable adminSecret
}
try {
    $adminHeaders = Get-DemoSession $adminEmail $adminPassword
    $branches = Invoke-DemoRequest 'GET' '/api/v1/admin/branches?size=100' $null $adminHeaders 200
    $demoBranch = @($branches.items | Where-Object active)[0]
    $demoSuffix = [guid]::NewGuid().ToString('N')
    $barberEmail = "barber-$demoSuffix@example.test"; $barberPassword = New-DemoPassword
    $barber = Invoke-DemoRequest 'POST' '/api/v1/admin/barbers' @{
        branchId=$demoBranch.id
        account=@{name='Barbero de demostración'; phone='0000000000'; email=$barberEmail; password=$barberPassword}
    } $adminHeaders 201
    $barberHeaders = Get-DemoSession $barberEmail $barberPassword
    $clientEmail = "client-$demoSuffix@example.test"; $clientPassword = New-DemoPassword
    $client = Invoke-DemoRequest 'POST' '/api/v1/auth/register' @{
        name='Cliente de demostración'; phone='0000000000'; email=$clientEmail; password=$clientPassword
    } $null 201
    $clientHeaders = Get-DemoSession $clientEmail $clientPassword
    $catalog = Invoke-DemoRequest 'GET' '/api/v1/services?size=100' $null $clientHeaders 200
    $haircut = @($catalog.items | Where-Object type -eq 'HAIRCUT')[0]
    $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('America/Mexico_City')
    $businessToday = [TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow,$businessZone).Date
    $slot = $null
    for ($dayOffset=0; $dayOffset -lt 14 -and $null -eq $slot; $dayOffset++) {
        $dateText = $businessToday.AddDays($dayOffset).ToString('yyyy-MM-dd')
        $availability = Invoke-DemoRequest 'GET' "/api/v1/appointments/availability?branchId=$($demoBranch.id)&barberId=$($barber.id)&date=$dateText" $null $clientHeaders 200
        $slot = $availability.slots | Select-Object -First 1
    }
    if ($null -eq $slot) { throw 'No hay horario disponible para la demostración en los próximos 14 días.' }
    $localStart = [TimeZoneInfo]::ConvertTime([DateTimeOffset]$slot.startsAt,$businessZone)
    $appointment = Invoke-DemoRequest 'POST' '/api/v1/appointments' @{
        barberId=$barber.id; date=$localStart.ToString('yyyy-MM-dd'); time=$localStart.ToString('HH:mm')
        primaryServiceId=$haircut.id; addOnIds=@()
    } $clientHeaders 201
    if ($AdjustDemoAppointmentClock) {
        Set-DemoAppointmentPast $appointment.id $client.id $barber.id
    } else {
        Write-Host 'Esperando el comienzo real del bloque reservado...'
        Wait-DemoUntil ([DateTimeOffset]$appointment.startsAt)
    }
    $barberHeaders = Get-DemoSession $barberEmail $barberPassword
    $null = Invoke-DemoRequest 'POST' "/api/v1/staff/appointments/$($appointment.id)/start" $null $barberHeaders 200
    if (-not $AdjustDemoAppointmentClock) { Wait-DemoUntil ([DateTimeOffset]$appointment.endsAt) }
    $barberHeaders = Get-DemoSession $barberEmail $barberPassword
    $null = Invoke-DemoRequest 'POST' "/api/v1/staff/appointments/$($appointment.id)/complete" $null $barberHeaders 200
    $charge = Invoke-DemoRequest 'POST' "/api/v1/staff/appointments/$($appointment.id)/charge" @{method='CASH'} $barberHeaders 201
    $null = Invoke-DemoRequest 'POST' "/api/v1/staff/appointments/$($appointment.id)/charge" @{method='CASH'} $barberHeaders 409
    $clientHeaders = Get-DemoSession $clientEmail $clientPassword
    $ownPayment = Invoke-DemoRequest 'GET' "/api/v1/appointments/$($appointment.id)/payment" $null $clientHeaders 200
    if (-not $ownPayment.paid) { throw 'La consulta propia no refleja el cobro.' }
    $adminHeaders = Get-DemoSession $adminEmail $adminPassword
    $product = Invoke-DemoRequest 'POST' '/api/v1/admin/products' @{
        sku="DEMO-$demoSuffix"; name='Producto de demostración'; price=49.90
    } $adminHeaders 201
    $null = Invoke-DemoRequest 'POST' "/api/v1/admin/products/$($product.id)/inventory/entries" @{
        branchId=$demoBranch.id; quantity=10; reason='Entrada para demostración presencial'
    } $adminHeaders 201
    $salePayload = @{method='CARD'; items=@(@{productId=$product.id; quantity=2})}
    $saleHeaders = $barberHeaders.Clone(); $saleHeaders['Idempotency-Key']=[guid]::NewGuid().ToString()
    $sale = Invoke-DemoRequest 'POST' '/api/v1/staff/sales' $salePayload $saleHeaders 201
    $replay = Invoke-DemoRequest 'POST' '/api/v1/staff/sales' $salePayload $saleHeaders 200
    if ($sale.id -ne $replay.id) { throw 'El reintento no devolvió la misma venta.' }
    $null = Invoke-DemoRequest 'GET' "/api/v1/staff/sales/$($sale.id)" $null $barberHeaders 200
    $inventory = Invoke-DemoRequest 'GET' "/api/v1/admin/products/$($product.id)" $null $adminHeaders 200
    $remaining = @($inventory.branches | Where-Object branchId -eq $demoBranch.id)[0].quantity
    if ($remaining -ne 8) { throw 'Existencias inesperadas después de la venta y su reintento.' }
    $paidLocalDate = [TimeZoneInfo]::ConvertTime([DateTimeOffset]$sale.paidAt,$businessZone).ToString('yyyy-MM-dd')
    $report = Invoke-DemoRequest 'GET' "/api/v1/admin/reports/income?period=DAY&date=$paidLocalDate&branchId=$($demoBranch.id)" $null $adminHeaders 200
    $spec = Invoke-DemoRequest 'GET' '/v3/api-docs' $null $null 200
    $null = Invoke-DemoRequest 'GET' '/swagger-ui/index.html' $null $null 200
    if ($spec.components.securitySchemes.bearerAuth.scheme -ne 'bearer') { throw 'Falta el esquema Bearer en OpenAPI.' }
    [pscustomobject]@{
        appointmentId=$appointment.id; chargeId=$charge.id; chargeAmount=$charge.amount
        saleId=$sale.id; saleAmount=$sale.total; remainingStock=$remaining
        reportDate=$paidLocalDate; servicesCollected=$report.servicesTotal; productsCollected=$report.productsTotal
        combinedCollected=$report.combinedTotal; swaggerUrl="$BaseUrl/swagger-ui/index.html"
        adjustedOnlyDemoAppointment=[bool]$AdjustDemoAppointmentClock
    }
} finally {
    Remove-Variable adminPassword,barberPassword,clientPassword,adminHeaders,barberHeaders,clientHeaders,saleHeaders -ErrorAction SilentlyContinue
}
