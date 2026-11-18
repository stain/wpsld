/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 */
module no.s11.wpsld.tui {
    requires no.s11.wpsld;
    requires com.fasterxml.jackson.databind;
    requires com.googlecode.lanterna;
    // Lanterna's theme initialisation reflects on java.awt.Color.
    requires java.desktop;
}
