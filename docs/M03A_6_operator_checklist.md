# M03A.6 bench-side operator checklist

Package/sample: ____________________  Cell: ____________________

Operator: ____________________  Notebook/page: ____________________

Use ink or a controlled electronic record. Leave unavailable measurements
`MISSING`; never enter assumed values. Initial each completed item.

- [ ] Define and record `sample_id` and `cell_id`. Initials: ______
- [ ] Record operator, date/time with timezone, and notebook page. Initials: ______
- [ ] Acquire independent EIS replicate 1 and assign a unique ID. Initials: ______
- [ ] Acquire independent EIS replicate 2 and assign a unique ID. Initials: ______
- [ ] Acquire independent EIS replicate 3 and assign a unique ID. Initials: ______
- [ ] Preserve every raw EIS instrument file unchanged. Initials: ______
- [ ] Export normalized EIS with every row retained. Initials: ______
- [ ] Confirm duplicate frequencies were not deleted. Initials: ______
- [ ] Confirm inductive points were not deleted. Initials: ______
- [ ] Confirm insufficient-HF information was not deleted or replaced. Initials: ______
- [ ] Confirm no smoothing, interpolation, or automatic outlier removal. Initials: ______
- [ ] Measure fixture resistance and retain raw evidence. Initials: ______
- [ ] Measure contact resistance and retain raw evidence. Initials: ______
- [ ] Measure membrane resistance and retain raw evidence. Initials: ______
- [ ] Measure other-series resistance and retain raw evidence. Initials: ______
- [ ] Measure electrode spacing and record its definition. Initials: ______
- [ ] Measure out-of-plane depth and record its definition. Initials: ______
- [ ] Measure geometric electrode area and record its definition. Initials: ______
- [ ] Measure EIS area and area basis. Initials: ______
- [ ] Measure current-density reporting area and definition. Initials: ______
- [ ] Complete independent direct-conductivity measurement and raw evidence. Initials: ______
- [ ] Complete EIS instrument/software and acquisition metadata. Initials: ______
- [ ] Complete temperature, electrolyte batch, water-content method and value. Initials: ______
- [ ] Complete gas, pressure, flow, and stabilization-time metadata. Initials: ______
- [ ] Register all evidence using package-relative paths. Initials: ______
- [ ] Confirm every path segment is free of junctions, symlinks, mount points, and other reparse points. Initials: ______
- [ ] Confirm role-directory binding and that `hashes/`/`reports/` contain no evidence. Initials: ______
- [ ] Confirm each `(sample_id, cell_id, replicate_id)` has exactly one raw and one normalized EIS file. Initials: ______
- [ ] Confirm there are no orphan or cross-sample/cross-cell evidence records. Initials: ______
- [ ] Confirm no evidence file is present but unregistered. Initials: ______
- [ ] Validate normalized header, nonempty rows, finite positive frequency, and finite impedance. Initials: ______
- [ ] Review row-map and conversion provenance paths, sizes, hashes, column map, time/offset, and processing flags. Initials: ______
- [ ] Freeze the completed package with a new ledger version. Initials: ______
- [ ] Review exact byte sizes, SHA-256, freeze time, timezone, and tool version. Initials: ______
- [ ] Confirm raw size, SHA-256, and `LastWriteTimeUtc` remained unchanged. Initials: ______
- [ ] Map each acquisition worksheet column to its same-name M03A.5 canonical column. Initials: ______
- [ ] Complete the unchanged M03A.5 formal configuration. Initials: ______
- [ ] Change only fully evidenced rows from `MISSING` to `MEASURED`. Initials: ______
- [ ] Run M03A.6 preflight and resolve every blocking finding. Initials: ______
- [ ] Run the published M03A.5 validator and submit strict review. Initials: ______

Gate acknowledgement: M03A.6 preflight does not declare experimental input
complete, authorize parameter transfer, or authorize M03B.
