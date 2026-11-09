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
package no.s11.wpsld.ui;

/**
 * How {@link EntityEditor} lays out the form, as a style's idioms have it
 * (see {@link Theme.Style#form()}), beyond what its stylesheet can change.
 */
enum FormLayout {
	/**
	 * Folding groups of rows; each value with × to remove it, + below to add
	 * one, and ? showing a field's description below it
	 */
	LIST(false, false, false),
	/** The groups as the tabs of an OS/2 Warp Properties notebook, with pages */
	NOTEBOOK(true, false, false),
	/**
	 * As macOS forms: each value row ends in − and + (as in Mail's rules and
	 * Finder's search), adding a value below it; ⓘ shows a field's
	 * description in a popover, as ? is for help on macOS
	 */
	MAC(false, true, true);

	private final boolean notebook;
	private final boolean rowButtons;
	private final boolean popoverHelp;

	FormLayout(boolean notebook, boolean rowButtons, boolean popoverHelp) {
		this.notebook = notebook;
		this.rowButtons = rowButtons;
		this.popoverHelp = popoverHelp;
	}

	boolean notebook() {
		return notebook;
	}

	/** Whether each value row has − and + rather than ×, with + below */
	boolean rowButtons() {
		return rowButtons;
	}

	/** Whether descriptions show in a popover from ⓘ rather than below with ? */
	boolean popoverHelp() {
		return popoverHelp;
	}
}
