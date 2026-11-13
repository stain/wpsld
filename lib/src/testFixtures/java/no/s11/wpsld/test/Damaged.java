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
package no.s11.wpsld.test;

/**
 * A JUnit category of tests that read many damaged copies of files (see
 * {@link Damage}), to find readers of files' metadata that hang, run out
 * of memory or overflow their stack on untrusted input:
 * <code>@Category(Damaged.class)</code>. They take minutes, so they are
 * left out of <code>./gradlew build</code> and run by
 * <code>./gradlew damagedTest</code>, as GitHub Actions does on release
 * and monthly.
 */
public interface Damaged {
}
