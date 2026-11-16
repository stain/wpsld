/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * wpsld-cli: command-line tools with wpsld's functionality (see Main).
 */
module no.s11.wpsld.cli {
    requires no.s11.wpsld;
    requires com.fasterxml.jackson.databind;
    requires info.picocli;
    // picocli reads the commands' annotations and sets their fields
    opens no.s11.wpsld.cli to info.picocli;
}
