package dev.lansharedmap.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lansharedmap.client.map.ClientMapState;
import dev.lansharedmap.client.map.Waypoint;
import dev.lansharedmap.map.Dimensions;
import dev.lansharedmap.map.MapLimits;
import dev.lansharedmap.network.PlayerMarker;
import dev.lansharedmap.network.RequestSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public final class WorldMapScreen extends Screen {
	private static final int BACKGROUND = 0xF0101418;
	private static final int PANEL = 0xE814181C;
	private static final int BAR = 0xF0181E24;
	private static final int BUTTON = 0xFF2C3848;
	private static final int BUTTON_HOVER = 0xFF3C5168;
	private static final int TEXT = 0xFFE7EEF5;
	private static final int MUTED = 0xFF9AABBA;
	private static final int SELF = 0xFFFFD45A;
	private static final int WAYPOINT = 0xFFFF8A2A;
	private static final int OUTLINE = 0xFF101418;
	/** 4 pixels per block reads as 100%. */
	private static final double DEFAULT_PIXELS_PER_BLOCK = 4.0;
	private static final double MIN_PIXELS_PER_BLOCK = 1.0 / 64.0;
	private static final double MAX_PIXELS_PER_BLOCK = 48.0;
	private static final double WHEEL_ZOOM = 0.12;
	private static final double BUTTON_ZOOM = 1.15;

	private final List<Hotspot> hotspots = new ArrayList<>();
	private double cameraX;
	private double cameraZ;
	private double pixelsPerBlock = DEFAULT_PIXELS_PER_BLOCK;
	private boolean followPlayer = true;
	private boolean panning;
	private int panButton = -1;
	private double anchorMouseX;
	private double anchorMouseY;
	private double anchorCameraX;
	private double anchorCameraZ;
	private int mouseX;
	private int mouseY;

	public WorldMapScreen() {
		super(Component.translatable("lansharedmap.screen.title"));
	}

	@Override
	protected void init() {
		super.init();
		ClientMapState state = ClientMapState.INSTANCE;
		if (!state.hasHello() && !state.snapshotRequested() && ClientPlayNetworking.canSend(RequestSnapshotPayload.TYPE)) {
			state.markSnapshotRequested();
			ClientPlayNetworking.send(RequestSnapshotPayload.INSTANCE);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.followCamera();
		graphics.fill(0, 0, this.width, this.height, BACKGROUND);
		Layout layout = Layout.of(this.width, this.height);
		this.hotspots.clear();
		this.drawMap(graphics, layout);
		this.drawChrome(graphics, layout);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = event.button();
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = this.hotspots.size() - 1; i >= 0; i--) {
				Hotspot hotspot = this.hotspots.get(i);
				if (hotspot.contains(event.x(), event.y())) {
					hotspot.action().run();
					return true;
				}
			}
		}
		Layout layout = Layout.of(this.width, this.height);
		if (!layout.inMap(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			this.panning = true;
			this.panButton = button;
			this.anchorMouseX = event.x();
			this.anchorMouseY = event.y();
			this.anchorCameraX = this.cameraX;
			this.anchorCameraZ = this.cameraZ;
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.onRightClick(layout, event);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == this.panButton) {
			this.panning = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!this.panning || event.button() != this.panButton) {
			return super.mouseDragged(event, dx, dy);
		}
		double movedX = event.x() - this.anchorMouseX;
		double movedY = event.y() - this.anchorMouseY;
		if (this.followPlayer && movedX * movedX + movedY * movedY < 4.0) {
			return true;
		}
		if (this.followPlayer) {
			this.anchorCameraX = this.cameraX;
			this.anchorCameraZ = this.cameraZ;
			this.anchorMouseX = event.x();
			this.anchorMouseY = event.y();
			movedX = 0.0;
			movedY = 0.0;
		}
		this.followPlayer = false;
		this.cameraX = this.anchorCameraX - movedX / this.pixelsPerBlock;
		this.cameraZ = this.anchorCameraZ - movedY / this.pixelsPerBlock;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		Layout layout = Layout.of(this.width, this.height);
		double anchorX = layout.inMap(mouseX, mouseY) ? mouseX : layout.mapCenterX();
		double anchorY = layout.inMap(mouseX, mouseY) ? mouseY : layout.mapCenterY();
		this.zoomAt(anchorX, anchorY, Math.exp(scrollY * WHEEL_ZOOM));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int center = InputConstants.KEY_C;
		if (event.key() == center || event.keycode() == center) {
			this.centerOnPlayer();
			return true;
		}
		return super.keyPressed(event);
	}

	private void followCamera() {
		LocalPlayer player = this.minecraft.player;
		if (this.followPlayer && player != null) {
			this.cameraX = player.getX();
			this.cameraZ = player.getZ();
		}
	}

	private void centerOnPlayer() {
		this.followPlayer = true;
		this.followCamera();
	}

	private void zoomAt(double screenX, double screenY, double factor) {
		if (!(factor > 0.0) || factor == 1.0) {
			return;
		}
		Layout layout = Layout.of(this.width, this.height);
		double worldX = this.blockX(layout, screenX);
		double worldZ = this.blockZ(layout, screenY);
		double next = Mth.clamp(this.pixelsPerBlock * factor, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
		if (next == this.pixelsPerBlock) {
			return;
		}
		this.pixelsPerBlock = next;
		this.cameraX = worldX - (screenX - layout.mapCenterX()) / this.pixelsPerBlock;
		this.cameraZ = worldZ - (screenY - layout.mapCenterY()) / this.pixelsPerBlock;
		this.followPlayer = false;
	}

	private void onRightClick(Layout layout, MouseButtonEvent event) {
		if (this.minecraft.player == null || this.minecraft.level == null) {
			return;
		}
		String dimension = Dimensions.id(this.minecraft.level);
		boolean shift = this.minecraft.hasShiftDown() || event.hasShiftDown();
		if (shift) {
			Waypoint nearest = this.nearestOnScreen(layout, dimension, event.x(), event.y());
			if (nearest != null) {
				ClientMapState.INSTANCE.removeWaypoint(nearest);
			}
			return;
		}
		int blockX = Mth.floor(this.blockX(layout, event.x()));
		int blockZ = Mth.floor(this.blockZ(layout, event.y()));
		ClientMapState.INSTANCE.addWaypoint(dimension, blockX, blockZ);
	}

	private Waypoint nearestOnScreen(Layout layout, String dimension, double mouseX, double mouseY) {
		Waypoint best = null;
		double bestDistance = 22.0 * 22.0;
		for (Waypoint waypoint : ClientMapState.INSTANCE.waypoints()) {
			if (!dimension.equals(waypoint.dimension())) {
				continue;
			}
			double dx = this.screenX(layout, waypoint.x() + 0.5) - mouseX;
			double dy = this.screenZ(layout, waypoint.z() + 0.5) - mouseY;
			double distance = dx * dx + dy * dy;
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = waypoint;
			}
		}
		return best;
	}

	private void drawMap(GuiGraphicsExtractor graphics, Layout layout) {
		graphics.enableScissor(layout.mapLeft, layout.mapTop, layout.mapRight, layout.mapBottom);
		if (this.minecraft.level != null) {
			String dimension = Dimensions.id(this.minecraft.level);
			Map<Long, int[]> chunks = ClientMapState.INSTANCE.chunks(dimension);
			int minChunkX = Mth.floor(this.blockX(layout, layout.mapLeft)) >> 4;
			int maxChunkX = Mth.floor(this.blockX(layout, layout.mapRight)) >> 4;
			int minChunkZ = Mth.floor(this.blockZ(layout, layout.mapTop)) >> 4;
			int maxChunkZ = Mth.floor(this.blockZ(layout, layout.mapBottom)) >> 4;
			for (Map.Entry<Long, int[]> entry : chunks.entrySet()) {
				int chunkX = ChunkPos.getX(entry.getKey());
				int chunkZ = ChunkPos.getZ(entry.getKey());
				if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
					continue;
				}
				this.drawChunk(graphics, layout, chunkX, chunkZ, entry.getValue());
			}
			this.drawGrid(graphics, layout);
			this.drawWaypoints(graphics, layout, dimension);
			this.drawPlayers(graphics, layout, dimension);
			this.drawCursor(graphics, layout);
		}
		graphics.disableScissor();
		graphics.outline(layout.mapLeft, layout.mapTop, layout.mapWidth(), layout.mapHeight(), 0xFF3D4C5C);
	}

	private void drawChunk(GuiGraphicsExtractor graphics, Layout layout, int chunkX, int chunkZ, int[] colors) {
		int step = this.pixelsPerBlock >= 1.0
			? 1
			: Mth.clamp(Mth.ceil(1.0 / this.pixelsPerBlock), 1, MapLimits.BLOCKS_PER_CHUNK);
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;
		for (int dz = 0; dz < MapLimits.BLOCKS_PER_CHUNK; dz += step) {
			int cellZ = Math.min(step, MapLimits.BLOCKS_PER_CHUNK - dz);
			int runX = 0;
			int runColor = 0;
			boolean running = false;
			for (int dx = 0; dx < MapLimits.BLOCKS_PER_CHUNK; dx += step) {
				int color = cellColor(colors, dx, dz, step);
				if (!running) {
					runX = dx;
					runColor = color;
					running = true;
					continue;
				}
				if (color == runColor) {
					continue;
				}
				this.fillBlocks(graphics, layout, originX + runX, originZ + dz, dx - runX, cellZ, runColor);
				runX = dx;
				runColor = color;
			}
			if (running) {
				this.fillBlocks(graphics, layout, originX + runX, originZ + dz, MapLimits.BLOCKS_PER_CHUNK - runX, cellZ, runColor);
			}
		}
	}

	private static int cellColor(int[] colors, int dx, int dz, int step) {
		int index = dx + dz * MapLimits.BLOCKS_PER_CHUNK;
		if (step <= 1) {
			return colors[index];
		}
		int x1 = Math.min(MapLimits.BLOCKS_PER_CHUNK, dx + step);
		int z1 = Math.min(MapLimits.BLOCKS_PER_CHUNK, dz + step);
		long red = 0;
		long green = 0;
		long blue = 0;
		int count = 0;
		for (int z = dz; z < z1; z++) {
			for (int x = dx; x < x1; x++) {
				int color = colors[x + z * MapLimits.BLOCKS_PER_CHUNK];
				red += (color >> 16) & 255;
				green += (color >> 8) & 255;
				blue += color & 255;
				count++;
			}
		}
		if (count == 0) {
			return colors[index];
		}
		return 0xFF000000 | ((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count);
	}

	private void fillBlocks(GuiGraphicsExtractor graphics, Layout layout, int blockX, int blockZ, int sizeX, int sizeZ, int color) {
		int x0 = this.screenX(layout, blockX);
		int y0 = this.screenZ(layout, blockZ);
		int x1 = this.screenX(layout, blockX + sizeX);
		int y1 = this.screenZ(layout, blockZ + sizeZ);
		if (x1 < x0) {
			int swap = x0;
			x0 = x1;
			x1 = swap;
		}
		if (y1 < y0) {
			int swap = y0;
			y0 = y1;
			y1 = swap;
		}
		if (x1 == x0) {
			x1++;
		}
		if (y1 == y0) {
			y1++;
		}
		if (x0 < layout.mapLeft) {
			x0 = layout.mapLeft;
		}
		if (y0 < layout.mapTop) {
			y0 = layout.mapTop;
		}
		if (x1 > layout.mapRight) {
			x1 = layout.mapRight;
		}
		if (y1 > layout.mapBottom) {
			y1 = layout.mapBottom;
		}
		if (x0 >= x1 || y0 >= y1) {
			return;
		}
		graphics.fill(x0, y0, x1, y1, color);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, Layout layout) {
		boolean blockGrid = this.pixelsPerBlock >= 10.0;
		boolean chunkGrid = this.pixelsPerBlock * MapLimits.BLOCKS_PER_CHUNK >= 14.0;
		if (!blockGrid && !chunkGrid) {
			return;
		}
		int minX = Mth.floor(this.blockX(layout, layout.mapLeft));
		int maxX = Mth.ceil(this.blockX(layout, layout.mapRight));
		int minZ = Mth.floor(this.blockZ(layout, layout.mapTop));
		int maxZ = Mth.ceil(this.blockZ(layout, layout.mapBottom));
		if (blockGrid) {
			for (int x = minX; x <= maxX; x++) {
				if ((x & 15) == 0) {
					continue;
				}
				int sx = this.screenX(layout, x);
				graphics.fill(sx, layout.mapTop, sx + 1, layout.mapBottom, 0x28000000);
			}
			for (int z = minZ; z <= maxZ; z++) {
				if ((z & 15) == 0) {
					continue;
				}
				int sy = this.screenZ(layout, z);
				graphics.fill(layout.mapLeft, sy, layout.mapRight, sy + 1, 0x28000000);
			}
		}
		if (chunkGrid) {
			int startX = minX & ~15;
			int startZ = minZ & ~15;
			for (int x = startX; x <= maxX; x += MapLimits.BLOCKS_PER_CHUNK) {
				int sx = this.screenX(layout, x);
				graphics.fill(sx, layout.mapTop, sx + 1, layout.mapBottom, 0x80384858);
			}
			for (int z = startZ; z <= maxZ; z += MapLimits.BLOCKS_PER_CHUNK) {
				int sy = this.screenZ(layout, z);
				graphics.fill(layout.mapLeft, sy, layout.mapRight, sy + 1, 0x80384858);
			}
		}
	}

	private void drawCursor(GuiGraphicsExtractor graphics, Layout layout) {
		if (!layout.inMap(this.mouseX, this.mouseY)) {
			return;
		}
		Font font = this.font;
		int blockX = Mth.floor(this.blockX(layout, this.mouseX));
		int blockZ = Mth.floor(this.blockZ(layout, this.mouseY));
		String label = blockX + ", " + blockZ;
		int width = font.width(label);
		int x = this.mouseX + 12;
		int y = this.mouseY + 14;
		if (x + width > layout.mapRight - 2) {
			x = this.mouseX - width - 8;
		}
		if (y + 10 > layout.mapBottom) {
			y = this.mouseY - 14;
		}
		x = Mth.clamp(x, layout.mapLeft + 2, Math.max(layout.mapLeft + 2, layout.mapRight - width - 2));
		y = Mth.clamp(y, layout.mapTop + 2, Math.max(layout.mapTop + 2, layout.mapBottom - 12));
		graphics.text(font, label, x, y, TEXT, true);
	}

	private void drawWaypoints(GuiGraphicsExtractor graphics, Layout layout, String dimension) {
		Font font = this.font;
		boolean names = this.pixelsPerBlock >= 0.75;
		for (Waypoint waypoint : ClientMapState.INSTANCE.waypoints()) {
			if (!dimension.equals(waypoint.dimension())) {
				continue;
			}
			int x = this.screenX(layout, waypoint.x() + 0.5);
			int y = this.screenZ(layout, waypoint.z() + 0.5);
			graphics.fill(x - 2, y - 2, x + 4, y + 4, OUTLINE);
			graphics.fill(x - 1, y - 1, x + 3, y + 3, WAYPOINT);
			graphics.fill(x, y - 16, x + 2, y, 0xFF3A2414);
			graphics.fill(x + 2, y - 16, x + 12, y - 9, WAYPOINT);
			graphics.fill(x + 2, y - 9, x + 8, y - 6, WAYPOINT);
			if (names) {
				graphics.text(font, waypoint.name(), x + 14, y - 16, TEXT, true);
			}
		}
	}

	private void drawPlayers(GuiGraphicsExtractor graphics, Layout layout, String dimension) {
		LocalPlayer self = this.minecraft.player;
		UUID selfId = self == null ? null : self.getUUID();
		Font font = this.font;
		boolean names = this.pixelsPerBlock >= 0.5;
		for (PlayerMarker marker : ClientMapState.INSTANCE.players()) {
			if (!dimension.equals(marker.dimension()) || marker.id().equals(selfId)) {
				continue;
			}
			this.drawMarker(graphics, font, layout, marker.x(), marker.z(), marker.yaw(), colorFor(marker.name()), names ? marker.name() : null, 8);
		}
		if (self != null && dimension.equals(Dimensions.id(self.level()))) {
			this.drawMarker(graphics, font, layout, self.getX(), self.getZ(), self.getYRot(), SELF, self.getName().getString(), 10);
		}
	}

	private void drawMarker(GuiGraphicsExtractor graphics, Font font, Layout layout, double blockX, double blockZ, float yaw, int color, String name, int length) {
		int x = this.screenX(layout, blockX);
		int y = this.screenZ(layout, blockZ);
		double radians = Math.toRadians(yaw);
		double forwardX = -Math.sin(radians);
		double forwardY = Math.cos(radians);
		double sideX = -forwardY;
		double sideY = forwardX;
		int back = Math.max(4, length / 2);
		int half = Math.max(4, (length * 2) / 3);
		int tipX = x + (int) Math.round(forwardX * length);
		int tipY = y + (int) Math.round(forwardY * length);
		int leftX = x + (int) Math.round(-forwardX * back + sideX * half);
		int leftY = y + (int) Math.round(-forwardY * back + sideY * half);
		int rightX = x + (int) Math.round(-forwardX * back - sideX * half);
		int rightY = y + (int) Math.round(-forwardY * back - sideY * half);
		fillTriangle(graphics, push(tipX, x, 1.45), push(tipY, y, 1.45), push(leftX, x, 1.45), push(leftY, y, 1.45), push(rightX, x, 1.45), push(rightY, y, 1.45), OUTLINE);
		fillTriangle(graphics, tipX, tipY, leftX, leftY, rightX, rightY, color);
		if (name != null) {
			int nameWidth = font.width(name);
			graphics.text(font, name, x - nameWidth / 2, y - length - 12, TEXT, true);
		}
	}

	private static int push(int point, int origin, double scale) {
		return origin + (int) Math.round((point - origin) * scale);
	}

	private static void fillTriangle(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
		if (y0 > y1) {
			int swapX = x0;
			int swapY = y0;
			x0 = x1;
			y0 = y1;
			x1 = swapX;
			y1 = swapY;
		}
		if (y1 > y2) {
			int swapX = x1;
			int swapY = y1;
			x1 = x2;
			y1 = y2;
			x2 = swapX;
			y2 = swapY;
		}
		if (y0 > y1) {
			int swapX = x0;
			int swapY = y0;
			x0 = x1;
			y0 = y1;
			x1 = swapX;
			y1 = swapY;
		}
		if (y2 == y0) {
			int left = Math.min(x0, Math.min(x1, x2));
			int right = Math.max(x0, Math.max(x1, x2)) + 1;
			graphics.fill(left, y0, right, y0 + 1, color);
			return;
		}
		for (int y = y0; y <= y2; y++) {
			double longX = x0 + (x2 - x0) * ((y - y0) / (double) (y2 - y0));
			double shortX;
			if (y < y1 && y1 != y0) {
				shortX = x0 + (x1 - x0) * ((y - y0) / (double) (y1 - y0));
			} else if (y2 != y1) {
				shortX = x1 + (x2 - x1) * ((y - y1) / (double) (y2 - y1));
			} else {
				shortX = x1;
			}
			int left = Mth.floor(Math.min(longX, shortX));
			int right = Mth.floor(Math.max(longX, shortX)) + 1;
			graphics.fill(left, y, right, y + 1, color);
		}
	}

	private void drawChrome(GuiGraphicsExtractor graphics, Layout layout) {
		Font font = this.font;
		graphics.fill(0, 0, this.width, layout.top, BAR);
		graphics.fill(0, layout.bottom, this.width, this.height, BAR);
		LocalPlayer player = this.minecraft.player;
		String dimensionId = this.minecraft.level == null ? "" : Dimensions.id(this.minecraft.level);
		Component dimension = dimensionName(dimensionId);
		int x = player == null ? 0 : Mth.floor(player.getX());
		int z = player == null ? 0 : Mth.floor(player.getZ());
		int explored = dimensionId.isEmpty() ? 0 : ClientMapState.INSTANCE.chunkCount(dimensionId);
		String cursor = "—";
		if (layout.inMap(this.mouseX, this.mouseY)) {
			cursor = Mth.floor(this.blockX(layout, this.mouseX)) + ", " + Mth.floor(this.blockZ(layout, this.mouseY));
		}
		Component status = Component.translatable(
			"lansharedmap.screen.status",
			dimension,
			x,
			z,
			cursor,
			explored
		);
		graphics.text(font, this.title, 8, 8, TEXT, false);
		String scale = formatScale(this.pixelsPerBlock);
		int scaleX = (layout.panel ? layout.panelLeft : this.width) - font.width(scale) - 10;
		graphics.text(font, scale, scaleX, 8, TEXT, false);
		graphics.text(font, status, 8, 20, MUTED, false);
		graphics.text(font, Component.translatable("lansharedmap.screen.north"), layout.mapLeft + 8, layout.mapTop + 6, TEXT, true);
		Component hint = Component.translatable("lansharedmap.screen.hint");
		if (font.width(hint) > this.width - 168) {
			hint = Component.translatable("lansharedmap.screen.hint_short");
		}
		graphics.text(font, hint, 8, this.height - 16, MUTED, false);

		this.button(graphics, font, this.width - 148, this.height - 22, 40, "lansharedmap.screen.zoom_out", () -> this.zoomAt(layout.mapCenterX(), layout.mapCenterY(), 1.0 / BUTTON_ZOOM));
		this.button(graphics, font, this.width - 104, this.height - 22, 40, "lansharedmap.screen.zoom_in", () -> this.zoomAt(layout.mapCenterX(), layout.mapCenterY(), BUTTON_ZOOM));
		this.button(graphics, font, this.width - 60, this.height - 22, 52, "lansharedmap.screen.center", this::centerOnPlayer);

		if (layout.panel) {
			this.drawPanel(graphics, font, layout, dimensionId);
		}
		if (!ClientMapState.INSTANCE.hasHello()) {
			graphics.centeredText(font, Component.translatable("lansharedmap.screen.waiting"), layout.mapCenterX(), layout.mapCenterY(), TEXT);
		} else if (explored == 0) {
			graphics.centeredText(font, Component.translatable("lansharedmap.screen.empty"), layout.mapCenterX(), layout.mapCenterY() + 16, MUTED);
		}
	}

	private void drawPanel(GuiGraphicsExtractor graphics, Font font, Layout layout, String dimensionId) {
		graphics.fill(layout.panelLeft, layout.top, this.width, layout.bottom, PANEL);
		int y = layout.top + 8;
		graphics.text(font, Component.translatable("lansharedmap.screen.players"), layout.panelLeft + 8, y, TEXT, false);
		y += 14;
		LocalPlayer self = this.minecraft.player;
		UUID selfId = self == null ? null : self.getUUID();
		boolean anyPlayer = false;
		for (PlayerMarker marker : ClientMapState.INSTANCE.players()) {
			if (y > layout.bottom - 78) {
				break;
			}
			anyPlayer = true;
			boolean here = dimensionId.equals(marker.dimension());
			boolean you = marker.id().equals(selfId);
			String suffix = you ? " (" + Component.translatable("lansharedmap.screen.you").getString() + ")" : "";
			String where = here ? "" : " · " + dimensionName(marker.dimension()).getString();
			int color = here ? TEXT : MUTED;
			graphics.text(font, Component.literal(marker.name() + suffix + where), layout.panelLeft + 8, y, color, false);
			y += 12;
		}
		if (!anyPlayer) {
			graphics.text(font, Component.translatable("lansharedmap.screen.none"), layout.panelLeft + 8, y, MUTED, false);
			y += 12;
		}
		y += 8;
		graphics.text(font, Component.translatable("lansharedmap.screen.waypoints"), layout.panelLeft + 8, y, TEXT, false);
		y += 14;
		int shown = 0;
		int otherDimensions = 0;
		for (Waypoint waypoint : ClientMapState.INSTANCE.waypoints()) {
			if (!dimensionId.equals(waypoint.dimension())) {
				otherDimensions++;
				continue;
			}
			if (y > layout.bottom - 16) {
				continue;
			}
			shown++;
			Waypoint current = waypoint;
			int row = y;
			this.hotspots.add(new Hotspot(layout.panelLeft + 4, row - 1, layout.panelWidth() - 22, 12, () -> this.focus(current)));
			graphics.text(font, Component.literal(waypoint.name() + "  " + waypoint.x() + ", " + waypoint.z()), layout.panelLeft + 8, row, TEXT, false);
			this.button(graphics, font, this.width - 18, row - 2, 14, "lansharedmap.screen.delete", () -> ClientMapState.INSTANCE.removeWaypoint(current));
			y += 14;
		}
		if (shown == 0) {
			graphics.text(font, Component.translatable("lansharedmap.screen.none"), layout.panelLeft + 8, y, MUTED, false);
			y += 12;
		}
		if (otherDimensions > 0 && y <= layout.bottom - 12) {
			graphics.text(font, Component.translatable("lansharedmap.screen.other_dimensions", otherDimensions), layout.panelLeft + 8, y, MUTED, false);
		}
	}

	private void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, String key, Runnable action) {
		boolean hover = this.mouseX >= x && this.mouseX < x + width && this.mouseY >= y && this.mouseY < y + 16;
		graphics.fill(x, y, x + width, y + 16, hover ? BUTTON_HOVER : BUTTON);
		Component label = Component.translatable(key);
		graphics.text(font, label, x + (width - font.width(label)) / 2, y + 4, TEXT, false);
		this.hotspots.add(new Hotspot(x, y, width, 16, action));
	}

	private void focus(Waypoint waypoint) {
		this.followPlayer = false;
		this.cameraX = waypoint.x() + 0.5;
		this.cameraZ = waypoint.z() + 0.5;
	}

	private int screenX(Layout layout, double blockX) {
		return Mth.floor(layout.mapCenterX() + (blockX - this.cameraX) * this.pixelsPerBlock);
	}

	private int screenZ(Layout layout, double blockZ) {
		return Mth.floor(layout.mapCenterY() + (blockZ - this.cameraZ) * this.pixelsPerBlock);
	}

	private double blockX(Layout layout, double screenX) {
		return this.cameraX + (screenX - layout.mapCenterX()) / this.pixelsPerBlock;
	}

	private double blockZ(Layout layout, double screenY) {
		return this.cameraZ + (screenY - layout.mapCenterY()) / this.pixelsPerBlock;
	}

	private static Component dimensionName(String id) {
		String path = id;
		int colon = id.indexOf(':');
		if (colon >= 0 && colon < id.length() - 1) {
			path = id.substring(colon + 1);
		}
		return switch (path) {
			case "overworld", "the_nether", "the_end" -> Component.translatable("lansharedmap.dim." + path);
			default -> Component.literal(path);
		};
	}

	private static String formatScale(double pixelsPerBlock) {
		double percent = pixelsPerBlock / DEFAULT_PIXELS_PER_BLOCK * 100.0;
		if (percent >= 10.0) {
			return Math.round(percent) + "%";
		}
		return String.format(Locale.ROOT, "%.1f%%", percent);
	}

	private static int colorFor(String name) {
		int hash = name.hashCode();
		int red = 90 + (hash & 0x7F);
		int green = 90 + ((hash >> 8) & 0x7F);
		int blue = 90 + ((hash >> 16) & 0x7F);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private record Hotspot(int x, int y, int width, int height, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	private record Layout(int top, int bottom, int mapLeft, int mapTop, int mapRight, int mapBottom, int panelLeft, boolean panel) {
		private static Layout of(int width, int height) {
			int top = 34;
			int bottom = height - 28;
			boolean panel = width >= 680;
			int panelLeft = panel ? width - 188 : width;
			return new Layout(top, bottom, 6, top + 2, panelLeft - 6, bottom - 2, panelLeft, panel);
		}

		private boolean inMap(double x, double y) {
			return x >= this.mapLeft && x < this.mapRight && y >= this.mapTop && y < this.mapBottom;
		}

		private int mapWidth() {
			return this.mapRight - this.mapLeft;
		}

		private int mapHeight() {
			return this.mapBottom - this.mapTop;
		}

		private int mapCenterX() {
			return (this.mapLeft + this.mapRight) / 2;
		}

		private int mapCenterY() {
			return (this.mapTop + this.mapBottom) / 2;
		}

		private int panelWidth() {
			return this.panel ? this.mapRight + 6 + 188 - this.panelLeft : 0;
		}
	}
}
