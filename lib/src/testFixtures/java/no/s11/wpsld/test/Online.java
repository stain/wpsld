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
 * A JUnit category of tests that use web services, such as ORCID or ROR:
 * <code>@Category(Online.class)</code>. They are left out of
 * <code>./gradlew build</code>, so that it neither fails when a service is
 * down nor asks services on every build, and run by
 * <code>./gradlew onlineTest</code>, as GitHub Actions does on release and
 * monthly. Keep them few, and their requests few.
 */
public interface Online {
}
