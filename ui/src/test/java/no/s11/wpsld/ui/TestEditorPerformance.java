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

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import javafx.stage.Window;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;
import no.s11.wpsld.test.Performance;

/**
 * Times the editor, step by step, on the crates of {@link PerformanceCrates}
 * and on a folder of 3 000 files: <code>./gradlew performanceTest</code>
 * (see build.gradle). Each step's time goes to the file the task names, and
 * a test fails, after all its steps, when any took longer than its limit
 * (times the task's slack). The limits are some three times what the steps
 * took on a developer's machine in November 2026 (at least a second, as
 * waiting for the JavaFX thread takes 0.1 s): lower one when its step is
 * made faster, so that it stays so.
 */
@Category(Performance.class)
public class TestEditorPerformance extends UiTest {
	private static final double SLACK = Double
			.parseDouble(System.getProperty("wpsld.test.performanceSlack", "1"));
	private static final Optional<Path> RESULTS = Optional
			.ofNullable(System.getProperty("wpsld.test.performanceResults")).map(Path::of);
	private static final Optional<Path> PROFILES = Optional
			.ofNullable(System.getProperty("wpsld.test.performanceProfile")).map(Path::of);

	@Rule
	public final TestName test = new TestName();

	private CrateEditor app;
	private Stage stage;
	private Path temp;
	/** The steps that took longer than their limits */
	private final List<String> slow = new ArrayList<>();
	private Optional<Recording> recording = Optional.empty();

	@Override
	protected long timeoutSeconds() {
		return 600;
	}

	@Override
	public void start(Stage stage) throws Exception {
		temp = Files.createTempDirectory("wpsld-performance");
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.init();
		app.start(stage);
		this.stage = stage;
	}

	@Before
	public void profile() throws IOException, ParseException {
		if (PROFILES.isPresent()) {
			Recording r = new Recording(Configuration.getConfiguration("profile"));
			r.start();
			recording = Optional.of(r);
		}
	}

	@After
	public void saveProfile() throws IOException {
		if (recording.isPresent()) {
			Files.createDirectories(PROFILES.get());
			recording.get().stop();
			recording.get().dump(PROFILES.get().resolve(test.getMethodName() + ".jfr"));
			recording.get().close();
		}
	}

	private interface Step {
		void run() throws Exception;
	}

	/**
	 * Runs a step and waits for the JavaFX thread to have done what it
	 * started; notes its time, the JavaFX thread's CPU time, and whether it
	 * was over its limit
	 */
	private void timed(String step, long limitMs, Step action) throws Exception {
		Thread fx = WaitForAsyncUtils.asyncFx(Thread::currentThread).get();
		ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		long cpu = threads.getThreadCpuTime(fx.threadId());
		long start = System.nanoTime();
		action.run();
		WaitForAsyncUtils.waitForFxEvents();
		long ms = (System.nanoTime() - start) / 1_000_000;
		long cpuMs = (threads.getThreadCpuTime(fx.threadId()) - cpu) / 1_000_000;
		long limit = Math.round(limitMs * SLACK);
		System.out.printf(Locale.ROOT, "PERF %s: %s: %d ms (limit %d ms), JavaFX thread %d ms%n",
				test.getMethodName(), step, ms, limit, cpuMs);
		if (RESULTS.isPresent()) {
			Files.writeString(RESULTS.get(), String.join("\t", test.getMethodName(), step, String.valueOf(ms),
					String.valueOf(limit), String.valueOf(cpuMs)) + "\n", StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		if (ms > limit) {
			slow.add(step + ": " + ms + " ms, over " + limit + " ms");
		}
	}

	private void assertNoneSlow() {
		assertTrue("Slower than their limits: " + String.join("; ", slow), slow.isEmpty());
	}

	private ListView<Entity> entities() throws Exception {
		waitForFx(60,
				() -> lookup("#entities").tryQuery().isPresent() && lookup("#save").tryQuery().isPresent());
		return onFx(() -> lookup("#entities").queryListView());
	}

	private MenuItem menuItem(String id) {
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		return bar.getMenus().stream().flatMap(m -> m.getItems().stream()).filter(i -> id.equals(i.getId()))
				.findFirst().orElseThrow();
	}

	/** A crate of {@link PerformanceCrates}, copied to be changed and saved */
	private Path crate(String name) throws IOException, URISyntaxException {
		Path from = Path.of(getClass().getResource("/performance/" + name + "/ro-crate-metadata.json").toURI());
		Path dir = Files.createDirectories(temp.resolve(name));
		return Files.copy(from, dir.resolve("ro-crate-metadata.json"));
	}

	/** Opens a crate, closing what it warns of, if anything */
	private void open(Path metadata, long limitMs) throws Exception {
		timed("open", limitMs, () -> assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, metadata)).get()));
		closeOtherWindows();
	}

	private void closeOtherWindows() {
		interact(() -> List.copyOf(Window.getWindows()).stream().filter(w -> w != stage && w instanceof Stage)
				.forEach(w -> ((Stage) w).close()));
	}

	private void select(ListView<Entity> list, int index) {
		interact(() -> {
			list.getSelectionModel().clearAndSelect(index);
			list.scrollTo(index);
		});
	}

	private void type(String fieldId, String text) {
		TextInputControl field = lookup(fieldId).queryAs(TextInputControl.class);
		interact(() -> {
			field.requestFocus();
			field.positionCaret(field.getText().length());
		});
		write(text);
	}

	/** View > Entities by Type, Folder or Part */
	private void view(String by) {
		interact(() -> ((RadioMenuItem) menuItem("menuListBy" + by)).setSelected(true));
	}

	private EntityTreeView tree() {
		return lookup("#entityTree").queryAs(EntityTreeView.class);
	}

	/**
	 * A crate of 3 200 entities of many types referring to each other:
	 * selecting, typing, undo, the trees and saving
	 */
	@Test
	public void largeCrate() throws Exception {
		ListView<Entity> list = entities();
		Path metadata = crate("large");
		open(metadata, 2000);
		timed("select root", 1000, () -> select(list, 0));
		timed("type 10 letters in root description", 3500, () -> type("#header-description", "abcdefghij"));
		timed("select entity 500", 1500, () -> select(list, 500));
		timed("select entity 1500", 1000, () -> select(list, 1500));
		timed("select entity 2500", 1000, () -> select(list, 2500));
		timed("type 5 letters in name", 2000, () -> type("#header-name", "hello"));
		timed("leave field", 1000, () -> interact(list::requestFocus));
		timed("undo", 1500, () -> interact(app::undo));
		timed("entities by folder", 1000, () -> view("Folder"));
		timed("expand all folders", 2000, () -> interact(() -> {
			tree().getSelectionModel().clearAndSelect(0);
			tree().expand(true);
		}));
		timed("select in tree", 1000, () -> interact(() -> tree().getSelectionModel().clearAndSelect(100)));
		timed("entities by part", 1000, () -> view("Part"));
		timed("entities by type", 1500, () -> view("Type"));
		timed("save", 1000,
				() -> assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, metadata)).get()));
		assertNoneSlow();
	}

	/**
	 * A root with 4 000 parts, 500 keywords and a long Markdown description,
	 * all by one person, whose preview lists all 4 000
	 */
	@Test
	public void wideRoot() throws Exception {
		ListView<Entity> list = entities();
		open(crate("wide"), 4500);
		timed("select root", 1000, () -> select(list, 0));
		timed("type 10 letters in root description", 3000, () -> type("#header-description", "abcdefghij"));
		timed("leave field", 1000, () -> interact(list::requestFocus));
		timed("undo", 2000, () -> interact(app::undo));
		int person = list.getItems().stream().map(Entity::getId).toList().indexOf("#person-0");
		timed("select the person all refer to", 3000, () -> select(list, person));
		timed("select a file", 1000, () -> select(list, 2000));
		timed("entities by part", 1500, () -> view("Part"));
		assertNoneSlow();
	}

	/** Folders ten deep, in the trees by folder and by part */
	@Test
	public void deepFolders() throws Exception {
		entities();
		open(crate("deep"), 1500);
		for (String view : List.of("folder", "part")) {
			timed("entities by " + view, 1500,
					() -> view(Character.toUpperCase(view.charAt(0)) + view.substring(1)));
			timed("expand all, by " + view, 1500, () -> interact(() -> {
				tree().getSelectionModel().clearAndSelect(0);
				tree().expand(true);
			}));
			timed("select the last, by " + view, 2500, () -> interact(() -> {
				int last = tree().getExpandedItemCount() - 1;
				tree().getSelectionModel().clearAndSelect(last);
				tree().scrollTo(last);
			}));
		}
		assertNoneSlow();
	}

	/**
	 * Copying 1 000 entities (variables measured, each referring to a unit)
	 * and pasting them: into their own crate (as copies with new @ids), into
	 * a large crate and into a new one; undo of each
	 */
	@Test
	public void copyAndPaste1000Entities() throws Exception {
		ListView<Entity> list = entities();
		open(crate("variables"), 1000);
		int[] variables = IntStream.range(0, list.getItems().size())
				.filter(i -> list.getItems().get(i).getTypes().contains("PropertyValue")).toArray();
		assertEquals(1000, variables.length);
		timed("select 1000", 1000, () -> interact(() -> {
			list.requestFocus();
			list.getSelectionModel().clearSelection();
			list.getSelectionModel().selectIndices(variables[0], variables);
		}));
		timed("copy 1000", 1000, () -> interact(menuItem("menuCopy")::fire));
		int before = list.getItems().size();
		timed("paste 1000 into the same crate", 1000, () -> interact(menuItem("menuPaste")::fire));
		closeOtherWindows();
		assertEquals(before + 1000, list.getItems().size());
		timed("undo the paste", 1000, () -> interact(app::undo));
		assertEquals(before, list.getItems().size());

		open(crate("large"), 2000);
		int large = list.getItems().size();
		timed("paste 1000 into a large crate", 1000, () -> interact(() -> {
			list.requestFocus();
			menuItem("menuPaste").fire();
		}));
		closeOtherWindows();
		// Not the units they refer to, which the large crate lacks
		assertEquals(large + 1000, list.getItems().size());
		timed("undo the paste into a large crate", 2000, () -> interact(app::undo));

		interact(() -> app.newCrate(stage));
		closeOtherWindows();
		timed("paste 1000 into a new crate", 1000, () -> interact(() -> {
			list.requestFocus();
			menuItem("menuPaste").fire();
		}));
		closeOtherWindows();
		assertTrue(list.getItems().size() > 1000);
		assertNoneSlow();
	}

	/**
	 * A folder of 30 folders of 100 files: opening it, the tree by folder,
	 * and adding all its files to the crate's parts at once
	 */
	@Test
	public void folderOfFiles() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("files"));
		for (int d = 0; d < 30; d++) {
			Path sub = Files.createDirectories(folder.resolve("folder" + d));
			for (int f = 0; f < 100; f++) {
				Files.writeString(sub.resolve("file" + f + ".txt"), "text " + f);
			}
		}
		entities();
		timed("open folder", 2000,
				() -> assertTrue(WaitForAsyncUtils.asyncFx(() -> app.openFolder(stage, folder)).get()));
		closeOtherWindows();
		timed("entities by folder", 1000, () -> view("Folder"));
		timed("expand all", 1000, () -> interact(() -> {
			tree().getSelectionModel().clearAndSelect(0);
			tree().expand(true);
		}));
		timed("select all files", 1000, () -> interact(() -> {
			tree().getSelectionModel().clearSelection();
			tree().getSelectionModel().selectRange(1, tree().getExpandedItemCount());
		}));
		timed("add 3030 files to has part", 3000, () -> interact(menuItem("menuAddPart")::fire));
		closeOtherWindows();
		timed("select root", 1000, () -> interact(() -> tree().getSelectionModel().clearAndSelect(0)));
		timed("undo", 1500, () -> interact(app::undo));
		assertNoneSlow();
	}
}
