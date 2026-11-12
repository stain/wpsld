# JavaScript libraries and editors

Status labels are from https://www.researchobject.org/ro-crate/tools (check it, they change).

**Libraries**
- `ro-crate-js`: `npm install ro-crate` (JavaScript, beta). `ro-crate-excel` (spreadsheets to crates, beta). `ro-crate-html` (preview, beta). RO-Crate Zip Explorer (TypeScript, read metadata inside zips, alpha).
- Other languages: ro-crate-java (builder API), rocrateR (CRAN), ro-crate-ruby, ro-crate-rs (also Python `rocraters`), ROCrates.Net, ro-crate-cpp.

**Editors (interactive)**
- Describo (production; desktop/web, profile-driven forms) https://describo.github.io
- Crate-O (beta; browser, vocabulary support) https://language-research-technology.github.io/crate-o/
- NovaCrate (production; web) https://novacrate.datamanager.kit.edu/
- RO-Crate Playground (JSON-LD or form editing in the browser) https://ro-crate.ldaca.edu.au/
- chaos2crate (alpha; turns a local folder into a crate in the browser)

When the user wants hand editing in a GUI, point them to these; an agent writing JSON should follow the `ro-crate-create` rules and validate afterwards.
