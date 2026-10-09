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

### `/idrepo/getNinByRegId`
Fetches the NIN for a single `reg_id` directly via the Packet Manager `searchField` API.  
**Request:** `GET /idrepo/getNinByRegId?regId=<reg_id>`  
**Response (JSON):** `{ "rid": "<reg_id>", "nin": "<NIN>" }`

---

### `/idrepo/checkNameInfo`
Reads `reg_id` / `process` from the default input file and, in batches, calls the Packet Manager `info` API to check whether `surname`, `givenName` and `otherNames` are present.  
**HTTP method:** POST (no request body / parameters)  
**Input file (hardcoded):** `packet-utility/src/main/resources/reg_process.csv` (columns: `reg_id`, `process`)  
**Output file:** `D:\output\name-info.csv`  
**Output columns:** `reg_id`, `process`, `surname`, `givenName`, `otherNames`, `remark`  
**Remark:** reflects presence — e.g. `All three fields are present.`, `2 of 3 fields are present: surname, givenName. Missing: otherNames.`, `1 of 3 fields is present: surname. Missing: givenName, otherNames.`, or `None of the three fields are present.`. `{present fields}` and `{missing fields}` are the actual field names.

---

### `/idrepo/ninstatus`
Fetches the status of given NINs from ID-Repo  
**Input file:** `packet-utility/src/main/resources/rids/nin_status.csv`

---

### `/idrepo/updateDetails`
Updates the following fields in ID-Repo for the given NIN:  
`surname`, `givenName`, `otherNames`, `gender`, `dateOfBirth`  
**Input file:** `packet-utility/src/main/resources/rids/nin_update.csv`

---

### `/idrepo/updateResidence`
Updates the residence fields in ID-Repo for the given NIN:  
`applicantPlaceOfResidenceDistrict`, `applicantPlaceOfResidenceCounty`, `applicantPlaceOfResidenceSubCounty`, `applicantPlaceOfResidenceParish`, `applicantPlaceOfResidenceVillage`  
**Input file:** `packet-utility/src/main/resources/nin_update.csv`

---

### `/idrepo/residenceDetails`
Fetches the applicant residence details from ID-Repo in batch.  
**Input file:** `packet-utility/src/main/resources/rids.csv`  
**Output file:** `D:\output\applicant_residence.csv`  
**Fields returned:** `applicantPlaceOfResidenceDistrict`, `applicantPlaceOfResidenceCounty`, `applicantPlaceOfResidenceSubCounty`, `applicantPlaceOfResidenceParish`, `applicantPlaceOfResidenceVillage`  
**Validation:** the residence hierarchy is validated against `packet-utility/src/main/resources/location_master_data.xlsx`. If the district → county → sub-county → parish → village chain matches the parent-child hierarchy in the sheet, the `remark` column is set to `matched`; otherwise it is `unmatched`. If no RID record exists in ID-Repo, the `remark` is `No record found in ID Repo`.
---

### `/idrepo/searchResidenceFields`
A simple, direct endpoint (like `/residenceStatus` / `/residenceDetails`) — no parameters needed. It reads `reg_id` and `process` from the default input CSV `packet-utility/src/main/resources/reg_process.csv`, and for every record calls the Packet Manager `searchFields` API to fetch the residence fields.  
**Output file:** `D:\output\residence-fields.csv`  
**Fields requested from Packet Manager:** `residenceStatus`, `applicantPlaceOfResidenceDistrict`, `applicantPlaceOfResidenceCounty`, `applicantPlaceOfResidenceSubCounty`, `applicantPlaceOfResidenceParish`, `applicantPlaceOfResidenceVillage`  
**Output columns:** `reg_id`, `process`, the 6 residence fields above, and `remark`. The `remark` stays empty (`null`) when the search succeeds; it is populated with the error / no-record message only if the lookup fails.

---

### `/idrepo/checkIdRepoStatus`
Reads `reg_id`s from the default input file (`packet-utility/src/main/resources/rids.csv`), calls **ID-Repo directly** for each one, and reports whether the application (reg_id) is present in ID-Repo — returning the NIN when it is.
**HTTP method:** GET (no request body / parameters)
**Input file:** `packet-utility/src/main/resources/rids.csv` (single column of `reg_id`s / RIDs)
**Output file:** `D:\output\idrepo-check.csv`
**Output columns:** `reg_id`, `status`, `nin`, `remark`
- `status` is `PRESENT_IN_IDREPO` when the reg_id is found (with `nin` populated), `NOT_FOUND` when it isn't, or `ERROR` if the lookup threw an exception (`remark` carries the exception message).
- When not found, `remark` is `No record found in ID Repo`.

---

### `/idrepo/checkIdRepoByRegId`
Checks a **single** `reg_id` against ID-Repo directly and returns whether it is present plus its NIN.
**Request:** `GET /idrepo/checkIdRepoByRegId?regId=<reg_id>`
**Response (JSON):** `{ "regId": "<reg_id>", "status": "PRESENT_IN_IDREPO" | "NOT_FOUND" | "ERROR", "nin": "<NIN or empty>", "remark": "<message or empty>" }`
