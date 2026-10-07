# Microsoft 365 setup (Phase 0)

This guide is for the tenant admin. Expect about an hour of hands-on work, plus up to 2 hours waiting for permissions to take effect.

When you're done:
- The meeting room is an Exchange **room mailbox** that Outlook and Teams can book.
- The room shows real meeting subjects.
- An Entra app can read and write the calendars of **room mailboxes only**. Exchange enforces that limit, not our code.

The commands were checked against Microsoft Learn on 2026-10-07. Sources are at the end. Replace `contoso.com`, `boardroom@…` and the names with your own.

> **Never paste the certificate's private key, client secret or tenant credentials into chat, a ticket, or the repo.** They go only into the backend's environment variables (Vercel).

---

## 0. Prerequisites

- Roles: **Exchange Administrator** in Entra plus membership of **Organization Management** in Exchange. A Global Administrator has both.
- PowerShell 7 with these modules:

```powershell
Install-Module ExchangeOnlineManagement -Scope CurrentUser
Install-Module Microsoft.Graph.Authentication, Microsoft.Graph.Calendar -Scope CurrentUser   # for the step-7 smoke test
Connect-ExchangeOnline -UserPrincipalName you@contoso.com
```

## 1. Create the room mailbox

Skip this step if the room already exists (check under Microsoft 365 admin centre → **Resources → Rooms & equipment**).

```powershell
New-Mailbox -Name "Board Room" -Alias boardroom -Room -PrimarySmtpAddress boardroom@contoso.com
```

A room mailbox needs **no licence** for booking and display.

## 2. Make the calendar keep subjects

By default Exchange replaces the room's copy of the subject with the organiser's name and strips the body. The panel would then show "Omar Al-Harthy" instead of "Product review".

```powershell
Set-CalendarProcessing -Identity boardroom@contoso.com `
  -AutomateProcessing AutoAccept `
  -DeleteSubject $false -AddOrganizerToSubject $false -DeleteComments $false `
  -RemovePrivateProperty $false `
  -ProcessExternalMeetingMessages $false

# Optional policy:
Set-CalendarProcessing -Identity boardroom@contoso.com -BookingWindowInDays 180 -MaximumDurationInMinutes 480 -AllowConflicts $false
Set-MailboxCalendarConfiguration -Identity boardroom@contoso.com -WorkingHoursTimeZone "Arabian Standard Time"
```

- `RemovePrivateProperty $false` keeps the private flag, so the panel shows "Private meeting" instead of the subject.
- `ProcessExternalMeetingMessages $false` (the default) means people outside your organisation can't book the room by email.
- **This only affects new bookings.** Existing meetings keep the organiser-name subject until they are re-sent.

## 3. Make it show up in Outlook Room Finder and Teams

Room Finder hides rooms that have no City, Building or Capacity.

```powershell
Set-Place -Identity boardroom@contoso.com -City "Muscat" -Building "HQ" -Floor 1 -FloorLabel "Ground" -Capacity 8 -Tags "Screen","Teams"

New-DistributionGroup -Name "HQ Rooms" -Alias hq-rooms -RoomList -Members boardroom@contoso.com
```

Room Finder can take up to 24 hours to show the room. Booking by typing the room's address works straight away.

## 4. Register the app in Entra

Entra admin centre → **App registrations → New registration**

1. **Name:** `Meeting Room Panel`. **Supported accounts:** this organisation only. **Redirect URI:** none.
2. **Certificates & secrets → Certificates → Upload certificate.**
   - Prefer a certificate over a client secret. Create one on your machine:

     ```bash
     openssl req -x509 -newkey rsa:2048 -sha256 -days 730 -nodes \
       -keyout mrp-graph.key -out mrp-graph.crt -subj "/CN=meeting-room-panel"
     ```

   - Upload `mrp-graph.crt`.
   - Keep `mrp-graph.key` safe; it goes into the backend's environment in step 8, and nowhere else.
3. **API permissions → Add → Microsoft Graph → Application permissions → `Place.Read.All` → Grant admin consent.**
   - `Place.Read.All` lets the admin page list rooms.
   - **Do not add any `Calendars.*` permission here.** Entra grants are tenant-wide and *add* to the Exchange scope in step 5. A `Calendars.ReadWrite` grant here would give the app every mailbox in the company.
   - If the template added `User.Read` (delegated), you can remove it.
4. Note the **Application (client) ID** and **Directory (tenant) ID** from Overview.
5. Go to **Enterprise applications → Meeting Room Panel** and note its **Object ID**.
   - This is the service principal's ID, **not** the one on the App registrations page. Microsoft warns the two differ.

## 5. Allow the app to access room calendars only (Exchange RBAC for Applications)

```powershell
# Make the app known to Exchange (IDs from step 4.4 and 4.5)
New-ServicePrincipal -AppId <application-client-id> -ObjectId <enterprise-app-object-id> -DisplayName "Meeting Room Panel"

# A scope that contains room mailboxes and nothing else
New-ManagementScope -Name "Room Mailboxes" -RecipientRestrictionFilter "RecipientTypeDetails -eq 'RoomMailbox'"

# Calendar read/write, limited to that scope
New-ManagementRoleAssignment -Name "MRP Rooms Calendars RW" -App <enterprise-app-object-id> `
  -Role "Application Calendars.ReadWrite" -CustomResourceScope "Room Mailboxes"
```

- If `New-ServicePrincipal` prompts for `ServiceId`, enter the same enterprise-app Object ID. That parameter is deprecated.
- **Want only some rooms?** Scope to the room list instead:

  ```powershell
  $dn = (Get-DistributionGroup hq-rooms).DistinguishedName
  New-ManagementScope -Name "HQ Rooms Scope" -RecipientRestrictionFilter "MemberOfGroup -eq '$dn'"
  ```

  Then use `-CustomResourceScope "HQ Rooms Scope"` above. Nested groups are not followed.

## 6. Verify the restriction

```powershell
Test-ServicePrincipalAuthorization -Identity <application-client-id> -Resource boardroom@contoso.com | Format-Table
Test-ServicePrincipalAuthorization -Identity <application-client-id> -Resource you@contoso.com      | Format-Table
```

| Mailbox | Expected `InScope` |
|---|---|
| `boardroom@…` | **True** |
| your own mailbox | **False** |

If your own mailbox shows `True`, stop. Something grants too much; check that step 4.3 has no `Calendars.*` permission.

The test bypasses Exchange's permission cache. Real API calls pick up the change within **30 minutes to 2 hours**.

## 7. Smoke test with the real app identity (after the cache delay)

```powershell
Connect-MgGraph -TenantId <tenant-id> -ClientId <application-client-id> -CertificateThumbprint <thumbprint>
# Windows: import mrp-graph.crt + key into CurrentUser\My first. macOS/Linux: use -Certificate with an X509Certificate2 built from a .pfx.

$start = (Get-Date).Date.ToUniversalTime().ToString("o"); $end = (Get-Date).Date.AddDays(1).ToUniversalTime().ToString("o")
Get-MgUserCalendarView -UserId boardroom@contoso.com -StartDateTime $start -EndDateTime $end | Select Subject, Start, End   # should work
Get-MgUserCalendarView -UserId you@contoso.com      -StartDateTime $start -EndDateTime $end                                # should FAIL (403)
```

Book a test meeting with the room from Outlook first. Its **real subject** should appear in the first command's output.

## 8. Hand-off values for the backend

| Value | Where it goes | Secret? |
|---|---|---|
| Tenant ID | `MS_TENANT_ID` | no |
| Application (client) ID | `MS_CLIENT_ID` | no |
| Certificate private key (`mrp-graph.key`, PEM) | `MS_CLIENT_CERT_KEY` | **yes** |
| Certificate thumbprint (shown in Entra after upload) | `MS_CLIENT_CERT_THUMBPRINT` | no |
| Room email | added in the admin page, not an env var | no |

Set them with `vercel env add` (or in the Vercel dashboard). Then delete the local `.key` file, or move it to your password manager.

## Undo

```powershell
Remove-ManagementRoleAssignment "MRP Rooms Calendars RW"
Remove-ManagementScope "Room Mailboxes"
Remove-ServicePrincipal -Identity <enterprise-app-object-id>
```

Deleting the app registration in Entra also removes its Exchange assignments.

## Sources

- RBAC for Applications: https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac
- New-ServicePrincipal: https://learn.microsoft.com/en-us/powershell/module/exchangepowershell/new-serviceprincipal
- New-ManagementScope: https://learn.microsoft.com/en-us/powershell/module/exchangepowershell/new-managementscope
- Filterable properties: https://learn.microsoft.com/en-us/powershell/exchange/recipientfilter-properties
- Test-ServicePrincipalAuthorization: https://learn.microsoft.com/en-us/powershell/module/exchangepowershell/test-serviceprincipalauthorization
- Set-CalendarProcessing: https://learn.microsoft.com/en-us/powershell/module/exchangepowershell/set-calendarprocessing
- Set-Place / Places: https://learn.microsoft.com/en-us/microsoft-365/places/get-started/quick-setup-buildings-floors
- List places (`Place.Read.All`): https://learn.microsoft.com/en-us/graph/api/place-list
- getSchedule: https://learn.microsoft.com/en-us/graph/api/calendar-getschedule

**Still to confirm in your tenant:**
- the `RecipientTypeDetails -eq 'RoomMailbox'` filter, which step 6 checks
- whether `getSchedule` permission checks apply to the URL mailbox, the `schedules` list, or both. The backend only ever queries rooms, so either is fine.
