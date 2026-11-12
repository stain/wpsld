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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import no.s11.wpsld.crate.Places.Area;

/**
 * A map of the world from {@link MapTiles}, in the web's Mercator
 * projection, showing a point or area. Drag to move it, scroll or use + and −
 * to zoom; a click (without dragging) picks a point, if the map is
 * {@link #setOnPick pickable}.
 */
public class MapView extends Region {
	private static final int MIN_ZOOM = 1;
	private static final int MAX_ZOOM = 19;
	/** The Mercator projection's limit, where the map is square */
	private static final double MAX_LATITUDE = 85.05112878;
	/** How far the mouse may move for a press to still be a click */
	private static final double CLICK_SLOP = 4;

	private final MapTiles tiles;
	private final Group tileLayer = new Group();
	private final Circle marker = new Circle(7);
	private final Rectangle area = new Rectangle();
	private final VBox zoomButtons;
	private final Label credit = new Label();
	private final Hyperlink creditLink = new Hyperlink();
	private final HBox creditBox;
	private int zoom = 2;
	private double latitude;
	private double longitude;
	private Optional<Area> shown = Optional.empty();
	private BiConsumer<Double, Double> onPick;
	private Point2D pressed;
	private Point2D dragged;

	public MapView(MapTiles tiles) {
		this.tiles = Objects.requireNonNull(tiles);
		getStyleClass().add("map-view");
		setId("mapView");
		setMinSize(160, 120);
		setPrefSize(480, 260);
		// The sea's colour, while tiles load or if there are none
		setStyle("-fx-background-color: #aad3df;");
		Rectangle clip = new Rectangle();
		clip.widthProperty().bind(widthProperty());
		clip.heightProperty().bind(heightProperty());
		setClip(clip);

		marker.setId("mapMarker");
		marker.setFill(Color.web("#d7263d"));
		marker.setStroke(Color.WHITE);
		marker.setStrokeWidth(2.5);
		marker.setMouseTransparent(true);
		area.setFill(Color.web("#d7263d", 0.15));
		area.setStroke(Color.web("#d7263d"));
		area.setStrokeWidth(2);
		area.setMouseTransparent(true);

		Button in = new Button("+");
		in.setId("mapZoomIn");
		in.setOnAction(e -> zoomBy(1, getWidth() / 2, getHeight() / 2));
		Button out = new Button("−");
		out.setId("mapZoomOut");
		out.setOnAction(e -> zoomBy(-1, getWidth() / 2, getHeight() / 2));
		in.setFocusTraversable(false);
		out.setFocusTraversable(false);
		in.setMinWidth(28);
		out.setMinWidth(28);
		zoomButtons = new VBox(2, in, out);
		zoomButtons.setPadding(new Insets(6));

		credit.setText(tiles.attribution());
		creditLink.setText(tiles.attribution());
		creditBox = new HBox(tiles.attributionPage().isPresent() ? creditLink : credit);
		creditBox.setAlignment(Pos.CENTER_RIGHT);
		creditBox.setStyle("-fx-background-color: rgba(255,255,255,0.8); -fx-font-size: 0.8em;");
		creditBox.setPadding(new Insets(0, 4, 0, 4));
		creditBox.setVisible(!tiles.attribution().isEmpty());
		credit.setTextFill(Color.web("#333"));

		getChildren().addAll(tileLayer, area, marker, zoomButtons, creditBox);
		area.setVisible(false);
		marker.setVisible(false);

		setOnMousePressed(e -> {
			if (e.getButton() == MouseButton.PRIMARY) {
				pressed = new Point2D(e.getX(), e.getY());
				dragged = pressed;
			}
		});
		setOnMouseDragged(e -> {
			if (dragged == null) {
				return;
			}
			setCursor(Cursor.MOVE);
			Point2D centre = worldPixel(latitude, longitude).add(dragged.getX() - e.getX(), dragged.getY() - e.getY());
			dragged = new Point2D(e.getX(), e.getY());
			centreOnPixel(centre);
		});
		setOnMouseReleased(e -> {
			setCursor(onPick == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
			if (pressed != null && onPick != null && pressed.distance(e.getX(), e.getY()) < CLICK_SLOP) {
				double[] point = pointAt(e.getX(), e.getY());
				onPick.accept(point[0], point[1]);
			}
			pressed = null;
			dragged = null;
		});
		setOnScroll(e -> {
			if (e.getDeltaY() != 0) {
				zoomBy(e.getDeltaY() > 0 ? 1 : -1, e.getX(), e.getY());
				e.consume();
			}
		});
	}

	/**
	 * Opens the credits' page when clicked, e.g. in the browser.
	 */
	public void setBrowse(Consumer<String> browse) {
		tiles.attributionPage().ifPresent(page -> creditLink.setOnAction(e -> browse.accept(page)));
	}

	/**
	 * Called with the latitude and longitude of a point clicked on the map;
	 * null for a map that only shows.
	 */
	public void setOnPick(BiConsumer<Double, Double> onPick) {
		this.onPick = onPick;
		setCursor(onPick == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
	}

	/**
	 * Show a point or area, centred at a zoom that fits it; none for nothing
	 * (keeping the map where it is).
	 */
	public void show(Optional<Area> area) {
		this.shown = area;
		area.ifPresent(a -> {
			int fitting = a.isPoint() ? 13 : fittingZoom(a);
			setView(fitting, a.latitude(), a.longitude());
		});
		requestLayout();
	}

	/**
	 * Show a point or area without moving the map, e.g. after the user picked
	 * it on the map.
	 */
	public void mark(Optional<Area> area) {
		this.shown = area;
		requestLayout();
	}

	public Optional<Area> getShown() {
		return shown;
	}

	public int getZoom() {
		return zoom;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	/**
	 * Centre the map on a point at a zoom level (1, the world, to 19).
	 */
	public void setView(int zoom, double latitude, double longitude) {
		this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
		this.latitude = Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, latitude));
		this.longitude = wrap(longitude);
		requestLayout();
	}

	private int fittingZoom(Area a) {
		double width = Math.max(getWidth(), getPrefWidth()) * 0.8;
		double height = Math.max(getHeight(), getPrefHeight()) * 0.8;
		for (int z = MAX_ZOOM; z > MIN_ZOOM; z--) {
			Point2D sw = worldPixel(a.south(), a.west(), z);
			Point2D ne = worldPixel(a.north(), a.east(), z);
			if (Math.abs(ne.getX() - sw.getX()) <= width && Math.abs(sw.getY() - ne.getY()) <= height) {
				return z;
			}
		}
		return MIN_ZOOM;
	}

	private void zoomBy(int steps, double x, double y) {
		int to = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + steps));
		if (to == zoom) {
			return;
		}
		// Keep the point under the mouse where it is
		double[] fixed = pointAt(x, y);
		zoom = to;
		Point2D fixedPixel = worldPixel(fixed[0], fixed[1]);
		centreOnPixel(fixedPixel.add(getWidth() / 2 - x, getHeight() / 2 - y));
	}

	private void centreOnPixel(Point2D pixel) {
		double[] point = point(pixel, zoom);
		setView(zoom, point[0], point[1]);
	}

	/** The latitude and longitude at a position on the map */
	double[] pointAt(double x, double y) {
		Point2D centre = worldPixel(latitude, longitude);
		return point(centre.add(x - getWidth() / 2, y - getHeight() / 2), zoom);
	}

	private Point2D worldPixel(double lat, double lon) {
		return worldPixel(lat, lon, zoom);
	}

	/** Web Mercator: the pixel of a point in the whole world's map at a zoom */
	private static Point2D worldPixel(double lat, double lon, int zoom) {
		double size = MapTiles.SIZE * Math.pow(2, zoom);
		double clamped = Math.toRadians(Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, lat)));
		double x = (lon + 180) / 360 * size;
		double y = (1 - Math.log(Math.tan(clamped) + 1 / Math.cos(clamped)) / Math.PI) / 2 * size;
		return new Point2D(x, y);
	}

	private static double[] point(Point2D pixel, int zoom) {
		double size = MapTiles.SIZE * Math.pow(2, zoom);
		double lon = pixel.getX() / size * 360 - 180;
		double n = Math.PI * (1 - 2 * pixel.getY() / size);
		double lat = Math.toDegrees(Math.atan(Math.sinh(n)));
		return new double[] { Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, lat)), wrap(lon) };
	}

	private static double wrap(double longitude) {
		double wrapped = ((longitude + 180) % 360 + 360) % 360 - 180;
		return wrapped == -180 && longitude > 0 ? 180 : wrapped;
	}

	@Override
	protected void layoutChildren() {
		double width = getWidth();
		double height = getHeight();
		Point2D centre = worldPixel(latitude, longitude);
		double left = centre.getX() - width / 2;
		double top = centre.getY() - height / 2;
		int count = 1 << zoom;
		tileLayer.getChildren().clear();
		int firstX = (int) Math.floor(left / MapTiles.SIZE);
		int lastX = (int) Math.floor((left + width) / MapTiles.SIZE);
		int firstY = Math.max(0, (int) Math.floor(top / MapTiles.SIZE));
		int lastY = Math.min(count - 1, (int) Math.floor((top + height) / MapTiles.SIZE));
		for (int tx = firstX; tx <= lastX; tx++) {
			for (int ty = firstY; ty <= lastY; ty++) {
				// Around the world east and west
				int x = Math.floorMod(tx, count);
				ImageView view = new ImageView();
				view.setX(tx * MapTiles.SIZE - left);
				view.setY(ty * MapTiles.SIZE - top);
				tileLayer.getChildren().add(view);
				CompletableFuture<Optional<Image>> tile = tiles.tile(zoom, x, ty);
				if (tile.isDone()) {
					// Loaded before: at once, so the map doesn't flicker
					tile.join().ifPresent(view::setImage);
				} else {
					tile.thenAccept(image -> image.ifPresent(i -> Platform.runLater(() -> view.setImage(i))));
				}
			}
		}

		marker.setVisible(false);
		area.setVisible(false);
		shown.ifPresent(a -> {
			if (a.isPoint()) {
				Point2D p = worldPixel(a.latitude(), a.longitude());
				marker.setCenterX(nearest(p.getX(), centre.getX(), count) - left);
				marker.setCenterY(p.getY() - top);
				marker.setVisible(true);
			} else {
				Point2D nw = worldPixel(a.north(), a.west());
				Point2D se = worldPixel(a.south(), a.east());
				double x = nearest(nw.getX(), centre.getX(), count) - left;
				area.setX(x);
				area.setY(nw.getY() - top);
				area.setWidth(Math.max(2, se.getX() - nw.getX()));
				area.setHeight(Math.max(2, se.getY() - nw.getY()));
				area.setVisible(true);
			}
		});

		zoomButtons.resizeRelocate(0, 0, zoomButtons.prefWidth(-1), zoomButtons.prefHeight(-1));
		double creditWidth = Math.min(width, creditBox.prefWidth(-1));
		double creditHeight = creditBox.prefHeight(creditWidth);
		creditBox.resizeRelocate(width - creditWidth, height - creditHeight, creditWidth, creditHeight);
	}

	/**
	 * The x of a world pixel in the copy of the world nearest the centre, so
	 * a place near the date line shows on the side being looked at.
	 */
	private static double nearest(double x, double centreX, int count) {
		double world = (double) MapTiles.SIZE * count;
		return x + Math.round((centreX - x) / world) * world;
	}
}
