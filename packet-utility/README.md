# Read Me First

Utility for Packet Manager & ID-Repo services.

---

## Getting Started

### Config Setup

Set the following properties in `application.properties`:

- `io.mosip.domain.url`
- `io.mosip.output.file.path` (provide path for output file)

Ensure that **`kernel-auth-adapter`** JAR is added to your classpath.

---

## API Endpoints

### `/idrepo/getnin`
Fetches NINs for given RIDs  
**Input file:** `packet-utility/src/main/resources/rids.csv`

---

### `/idrepo/ninstatus`
Fetches the status of given NINs from ID-Repo  
**Input file:** `packet-utility/src/main/resources/rids/nin_status.csv`

---

### `/idrepo/updateDetails`
Updates the following fields in ID-Repo for the given NIN:  
`surname`, `givenName`, `otherNames`, `gender`, `dateOfBirth`  
**Input file:** `packet-utility/src/main/resources/rids/nin_update.csv`
