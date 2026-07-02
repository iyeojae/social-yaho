<#
.SYNOPSIS
Docker MySQL에 저장된 문학 데이터의 UTF-8/utf8mb4 상태를 점검합니다.

.DESCRIPTION
다음 항목을 한 번에 확인합니다.
1) 스키마/테이블 charset 및 collation
2) 원문 컬럼 직접 조회 결과
3) HEX 저장값 확인
4) HEX -> UTF-8 디코드 결과

.PARAMETER ContainerName
MySQL이 실행 중인 Docker 컨테이너 이름입니다.

.PARAMETER Database
조회할 데이터베이스 이름입니다.

.PARAMETER User
MySQL 사용자명입니다.

.PARAMETER Password
MySQL 비밀번호입니다.

.PARAMETER Ids
확인할 literature_works.id 목록입니다. 쉼표 또는 공백 구분 문자열을 받습니다.
예: "15055,15054,15050,15036"

.PARAMETER RecentLimit
Ids를 비웠을 때 최근 몇 건을 볼지 지정합니다.

.PARAMETER ExportPath
결과를 UTF-8 텍스트 파일로 저장할 경로입니다.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\check-mysql-utf8.ps1

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\check-mysql-utf8.ps1 -Ids "15055,15054,15050,15036" -ExportPath ".\build\reports\utf8-check.txt"
#>
param(
    [string]$ContainerName = "klit-mysql",
    [string]$Database = "klit_db",
    [string]$User = "root",
    [string]$Password = "root",
    [string]$Ids = "15055,15054,15050,15036",
    [int]$RecentLimit = 10,
    [string]$ExportPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Set-Utf8Console {
    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    chcp 65001 > $null
}

function Invoke-MySqlQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query
    )

    $escapedQuery = $Query.Replace('"', '\"')
    $dockerArgs = @(
        'exec', '-e', ('MYSQL_PWD={0}' -f $Password), '-i', $ContainerName,
        'mysql', '--default-character-set=utf8mb4',
        ('-u{0}' -f $User),
        '-D', $Database,
        '-e', $escapedQuery
    )

    $output = & docker @dockerArgs 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed.`n$output"
    }
    return ($output -join [Environment]::NewLine)
}

function Convert-HexToUtf8String {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Hex
    )

    if ([string]::IsNullOrWhiteSpace($Hex) -or $Hex -eq 'NULL') {
        return ''
    }

    $clean = ($Hex -replace '\s+', '')
    if ($clean.Length % 2 -ne 0) {
        return '[invalid hex length]'
    }

    $bytes = for ($i = 0; $i -lt $clean.Length; $i += 2) {
        [Convert]::ToByte($clean.Substring($i, 2), 16)
    }

    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Write-Section {
    param([string]$Title)
    Write-Host "`n=== $Title ===" -ForegroundColor Cyan
}

Set-Utf8Console

$parsedIds = @()
if (-not [string]::IsNullOrWhiteSpace($Ids)) {
    $parsedIds = $Ids -split '[,\s]+' | Where-Object { $_ -match '^\d+$' }
}

$idClause = if ($parsedIds.Count -gt 0) {
    $parsedIds -join ', '
} else {
    ''
}

$summaryQuery = @"
SELECT DEFAULT_CHARACTER_SET_NAME AS default_charset, DEFAULT_COLLATION_NAME AS default_collation
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = '$Database';

SHOW CREATE TABLE literature_works;
SHOW CREATE TABLE genres;
"@

$rowsQuery = if ($idClause) {
@"
SELECT id, source_book_id, title, original_title, author_name, translated_language, isbn, source_type,
       CAST(content_available AS UNSIGNED) AS content_available
FROM literature_works
WHERE id IN ($idClause)
ORDER BY id DESC;
"@
} else {
@"
SELECT id, source_book_id, title, original_title, author_name, translated_language, isbn, source_type,
       CAST(content_available AS UNSIGNED) AS content_available
FROM literature_works
ORDER BY id DESC
LIMIT $RecentLimit;
"@
}

$hexQuery = if ($idClause) {
@"
SELECT id,
       HEX(title) AS title_hex,
       HEX(original_title) AS original_title_hex,
       HEX(author_name) AS author_name_hex,
       HEX(translated_language) AS translated_language_hex
FROM literature_works
WHERE id IN ($idClause)
ORDER BY id DESC;
"@
} else {
@"
SELECT id,
       HEX(title) AS title_hex,
       HEX(original_title) AS original_title_hex,
       HEX(author_name) AS author_name_hex,
       HEX(translated_language) AS translated_language_hex
FROM literature_works
ORDER BY id DESC
LIMIT $RecentLimit;
"@
}

$summaryOutput = Invoke-MySqlQuery -Query $summaryQuery
$rowsOutput = Invoke-MySqlQuery -Query $rowsQuery
$hexOutput = Invoke-MySqlQuery -Query $hexQuery

Write-Section 'Schema Charset Check'
Write-Output $summaryOutput

Write-Section 'Direct UTF-8 Display Check'
Write-Output $rowsOutput

Write-Section 'HEX Storage Check'
Write-Output $hexOutput

Write-Section 'PowerShell UTF-8 Decoding From HEX'
$hexLines = $hexOutput -split "`r?`n" | Where-Object { $_.Trim() }
$headerPassed = $false
foreach ($line in $hexLines) {
    if ($line -match '^id\s+') {
        $headerPassed = $true
        continue
    }
    if (-not $headerPassed) {
        continue
    }
    if ($line -like 'mysql:*') {
        continue
    }

    $parts = $line -split "`t"
    if ($parts.Count -lt 5) {
        $parts = $line -split '\s{2,}'
    }
    if ($parts.Count -lt 5) {
        continue
    }

    $id = $parts[0].Trim()
    $title = Convert-HexToUtf8String $parts[1].Trim()
    $originalTitle = Convert-HexToUtf8String $parts[2].Trim()
    $authorName = Convert-HexToUtf8String $parts[3].Trim()
    $translatedLanguage = Convert-HexToUtf8String $parts[4].Trim()

    Write-Output ("id={0}" -f $id)
    Write-Output ("  title                : {0}" -f $title)
    Write-Output ("  original_title       : {0}" -f $originalTitle)
    Write-Output ("  author_name          : {0}" -f $authorName)
    Write-Output ("  translated_language  : {0}" -f $translatedLanguage)
}

if ($ExportPath) {
    $report = @(
        '=== Schema Charset Check ===',
        $summaryOutput,
        '',
        '=== Direct UTF-8 Display Check ===',
        $rowsOutput,
        '',
        '=== HEX Storage Check ===',
        $hexOutput
    ) -join [Environment]::NewLine

    $directory = Split-Path -Parent $ExportPath
    if ($directory -and -not (Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
    $report | Out-File -FilePath $ExportPath -Encoding utf8
    Write-Host "`nUTF-8 report exported to: $ExportPath" -ForegroundColor Green
}

Write-Host "`nUTF-8 check completed." -ForegroundColor Green





