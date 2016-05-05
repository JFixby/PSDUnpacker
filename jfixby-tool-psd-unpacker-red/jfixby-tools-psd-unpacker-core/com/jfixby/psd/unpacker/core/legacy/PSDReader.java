
package com.jfixby.psd.unpacker.core.legacy;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Vector;

import com.jfixby.cmns.api.log.L;
import com.jfixby.cmns.api.sys.Sys;
import com.jfixby.psd.unpacker.api.PSD_BLEND_MODE;
import com.jfixby.psd.unpacker.core.PSDLayerImpl;

/**
 * Class PSDReader - Decodes a PhotoShop (.psd) file into one or more frames.
 * Supports uncompressed or RLE-compressed RGB files only. Each layer may be
 * retrieved as a full frame BufferedImage, or as a smaller image with an offset
 * if the layer does not occupy the full frame size. Transparency in the
 * original psd file is preserved in the returned BufferedImage's. Does not
 * support additional features in PS versions higher than 3.0. Example: <br>
 *
 * <pre>
 * PSDReader r = new PSDReader();
 * r.read(&quot;sample.psd&quot;);
 * int n = r.getFrameCount();
 * for (int i = 0; i &lt; n; i++) {
 * 	BufferedImage image = r.getLayer(i);
 * 	Point offset = r.getLayerOffset(i);
 * 	// do something with image
 * }
 * </pre>
 *
 * No copyright asserted on the source code of this class. May be used for any
 * purpose. Please forward any corrections to kweiner@fmsware.com.
 *
 * @author Kevin Weiner, FM Software.
 * @version 1.1 January 2004 [bug fix; add RLE support]
 *
 */

/** @author JFixby https://github.com/JFixby/ */

public class PSDReader {

	FileContent content;

	public static int ImageType = BufferedImage.TYPE_INT_ARGB;

	protected InputStream input;

	protected Status status = Status.NEW;

	protected final Header header = new Header();

	final ImageResourcesSection image_resources_section = new ImageResourcesSection();

	// protected int nLayers;

	protected int layerMaskSectionLen;

	protected boolean hasLayers;

	protected short[] lineLengths;

	protected int lineIndex;

	protected boolean rleEncoded;

	private boolean crash_on_mask;

	/** Gets the number of layers read from file.
	 *
	 * @return frame count */
	// public int getFrameCount() {
	// return frames.size();
	// }

	protected void setInput (final InputStream stream) {
		// open input stream
		this.init();
		if (stream == null) {
			this.setStatus(Status.STATUS_OPEN_ERROR);
		} else {
			if (stream instanceof BufferedInputStream) {
				this.input = stream;
			} else {
				this.input = new BufferedInputStream(stream);
			}
		}
	}

	private void setStatus (final Status statusOpenError) {
		this.status = statusOpenError;
		// Log.d("setStatus", this.status);
		if (this.status.printStack()) {
			new Error(this.status + "").printStackTrace();
			Sys.exit();
		}

	}

	private void setStatus (final Status statusOpenError, final Exception e) {
		this.status = statusOpenError;
		// Log.d("setStatus", this.status);
		if (this.status.printStack()) {
			e.printStackTrace();
			Sys.exit();
		}

	}

	protected void setInput (String name) {
		// open input file
		this.init();
		try {
			name = name.trim();
			if (name.startsWith("file:")) {
				name = name.substring(5);
				while (name.startsWith("/")) {
					name = name.substring(1);
				}
			}
			if (name.indexOf("://") > 0) {
				final URL url = new URL(name);
				this.input = new BufferedInputStream(url.openStream());
			} else {
				this.input = new BufferedInputStream(new FileInputStream(name));
			}
		} catch (final IOException e) {
			e.printStackTrace();
			this.setStatus(Status.STATUS_OPEN_ERROR);
		}
	}

	protected void setStream (final ByteArrayInputStream name) {
		// open input file
		this.init();
		// try {
		// name = name.trim();
		// if (name.startsWith("file:")) {
		// name = name.substring(5);
		// while (name.startsWith("/"))
		// name = name.substring(1);
		// }
		// if (name.indexOf("://") > 0) {
		// URL url = new URL(name);
		// input = new BufferedInputStream(url.openStream());
		// } else {
		// input = new BufferedInputStream(new FileInputStream(name));
		// }
		// } catch (IOException e) {
		// e.printStackTrace();
		// setStatus(Status.STATUS_OPEN_ERROR);
		// }

		this.input = name;
	}

	/** Reads PhotoShop layers from stream.
	 *
	 * @param InputStream in PhotoShop format.
	 * @return read status code (0 = no errors) */
	public Status read (final InputStream stream) {
		this.setInput(stream);
		this.process();
		return this.status;
	}

	/** Reads PhotoShop file from specified source (file or URL string)
	 *
	 * @param name String containing source
	 * @return read status code (0 = no errors) */
	public FileContent read (final String name) {
		final File f = new File(name);
		final String filename = f.getName();
		this.setInput(name);
		this.process();
		try {
			this.input.close();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		final FileContent result = this.content;
		result.setFileName(filename);
		this.content = null;
		return result;
	}

	public FileContent readFromStream (final String filename, final ByteArrayInputStream stream) {
		// File f = new File(name);
		// String filename = f.getName();
		this.setStream(stream);
		this.process();
		try {
			this.input.close();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		final FileContent result = this.content;
		result.setFileName(filename);
		this.content = null;
		return result;
	}

	/** Closes input stream and discards contents of all frames. */
	// public void reset() {
	// init();
	// }

	protected void close () {
		if (this.input != null) {
			try {
				this.input.close();
			} catch (final Exception e) {
			}
			this.input = null;
		}
	}

	protected boolean err () {
		return this.status != Status.STATUS_OK;
	}

	protected byte[] fillBytes (final int size, final int value) {
		// create byte array filled with given value
		final byte[] b = new byte[size];
		if (value != 0) {
			final byte v = (byte)value;
			for (int i = 0; i < size; i++) {
				b[i] = v;
			}
		}
		return b;
	}

	protected void init () {
		this.close();

		// layers = null;
		this.hasLayers = true;
		this.setStatus(Status.STATUS_OK);
	}

	// protected void makeDummyLayer(Vector<LayerInfo> layers) {
	// // creat dummy layer for non-layered image
	// short encoding = readShort();
	// L.d("encoding", encoding);
	//
	// rleEncoded = encoding == 1;
	// hasLayers = false;
	//
	// // nLayers = 1;
	// // layers = new LayerInfo[1];
	// LayerInfo layer = new LayerInfo();
	// // layers[0] = layer;
	// layers.add(layer);
	// layer.h = header.getHeight();
	// layer.w = header.getWidth();
	// int nc = Math.min(header.getNumberOfChannels(), 4);
	// if (rleEncoded) {
	// // get list of rle encoded line lengths for all channels
	// readLineLengths(header.getHeight() * nc);
	// }
	// // layer.nChan = nc;
	// // layer.chanID = new int[nc];
	// for (int i = 0; i < nc; i++) {
	// ChannelInfo chinf = new ChannelInfo();
	// layer.getChannels().add(chinf);
	// int id = i;
	// chinf.setChannelID(ChannelID.valueOfInt(id));
	// if (i == 3) {
	// chinf.setChannelID(ChannelID.valueOfInt(-1));
	// }
	// }
	//
	// }

	protected BufferedImage makeImage (final int w, final int h, final byte[] r, final byte[] g, final byte[] b, final byte[] a) {
		// create image from given plane data
		final BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final int[] data = ((DataBufferInt)im.getRaster().getDataBuffer()).getData();
		final int n = w * h;
		int j = 0;
		while (j < n) {
			try {
				final int ac = a[j] & 0xff;
				final int rc = r[j] & 0xff;
				final int gc = g[j] & 0xff;
				final int bc = b[j] & 0xff;
				data[j] = (((((ac << 8) | rc) << 8) | gc) << 8) | bc;
			} catch (final Exception e) {
				e.printStackTrace();
			}
			j++;
		}
		return im;
	}

	protected void process () {
		// decode PSD file
		if (this.err()) {
			return;
		}
		this.readHeader();
		if (this.err()) {
			return;
		}

		final Vector<LayerInfo> layers = new Vector<LayerInfo>();
		this.readLayerInfo(layers);

		// JUtils.newList(layers).print("found");

		if (this.err()) {
			return;
		}

		if (layers.size() == 0) {
			// makeDummyLayer(layers);
			if (this.err()) {
				return;
			}
		}
		this.readLayers(layers);
	}

	protected int readByte () {
		// read single byte from input
		int curByte = 0;
		try {
			curByte = this.input.read();
		} catch (final IOException e) {
			e.printStackTrace();
			this.setStatus(Status.STATUS_FORMAT_ERROR);
		}
		return curByte;
	}

	protected int readBytes (final byte[] bytes, final int n) {
		// read multiple bytes from input
		if (bytes == null) {
			return 0;
		}
		int r = 0;
		try {
			r = this.input.read(bytes, 0, n);
		} catch (final IOException e) {
			e.printStackTrace();
			this.setStatus(Status.STATUS_FORMAT_ERROR);
		}
		if (r < n) {
			// Log.d("bytes", Log.toHexString(bytes));
			L.d("r", r);
			L.d("n", n);
			this.setStatus(Status.STATUS_FORMAT_ERROR);

		}
		return r;
	}

	protected void readHeader () {
		// read PSD header info
		final String sig = this.readString(4);
		final int ver = this.readShort();

		this.header.setVersion(ver);

		this.skipBytes(6);
		final int nChan = this.readShort();
		final int height = this.readInt();
		final int width = this.readInt();
		this.header.setNumberOfChannels(nChan);
		this.header.setWidth(width);
		this.header.setHeight(height);
		this.header.setFileSignature(sig);

		final int depth = this.readShort();
		final int mode = this.readShort();

		this.header.setNumberOfBitsPerChannel(Depth.valueOf(depth));
		this.header.setColorMode(ColorMode.valueOf(mode));

		final int color_data_len = this.readInt();
		// Log.d("color_data_len", color_data_len);
		this.skipBytes(color_data_len);
		final int imagre_resources_len = this.readInt();
		// Log.d("imagre_resources_len", imagre_resources_len);
		this.readImageResourcesSection(imagre_resources_len);

		// require 8-bit RGB data
		if ((!sig.equals("8BPS")) || (ver != 1)) {
			L.d("sig", ">" + sig + "<" + this.header.getVersion() + " depth:" + depth + " mode:" + this.mode(mode));
			this.setStatus(Status.STATUS_FORMAT_ERROR);
		} else if ((depth != 8) || (mode != 3)) {
			L.d("sig", ">" + sig + "<" + this.header.getVersion() + " depth:" + depth + " mode:" + this.mode(mode));
			this.setStatus(Status.STATUS_UNSUPPORTED);
		}
		// Log.d("header", header);
	}

	private String mode (final int mode) {
		if (mode == 4) {
			return "CMYK";
		}
		if (mode == 3) {
			return "RGB";
		}
		return "" + mode;
	}

	private void readImageResourcesSection (final int imagre_resources_len) {
		int rest = imagre_resources_len;
		final String signature = this.readString(4);
		rest = rest - 4;
		;
		final int resourceID = this.readShort();
		rest = rest - 2;
		final ImageResourceID resource_id = ImageResourceID.valueOf(resourceID);
		// Log.d("signature", signature);
		// Log.d("resourceID", resourceID);
		// Log.d("resource_id", resource_id);

		// Log.d("rest", rest);

		if (resource_id == ImageResourceID.IPTC_NAA) {
			// scanFormat(rest);

			this.skip(rest);
		} else {
			this.skip(rest);
			// skipBytes(imagre_resources_len);
			// Log.d("str", str);
		}

	}

	protected int readInt () {
		// read big-endian 32-bit integer
		return (((((this.readByte() << 8) | this.readByte()) << 8) | this.readByte()) << 8) | this.readByte();
	}

	protected void readLayerInfo (final Vector<LayerInfo> layers) {
		// read layer header info
		this.layerMaskSectionLen = this.readInt();
		// Log.d("layerMaskSectionLen", layerMaskSectionLen);
		if (this.layerMaskSectionLen == 0) {
			return; // no layers, only base image
		}
		final int layerInfoLen = this.readInt();
		// L.d("layerInfoLen", layerInfoLen);
		// L.d("header", header);

		// int b1 = (byte) this.readByte();
		// int b2 = (byte) this.readByte();
		// Log.d("b1", b1);
		// Log.d("b2", b2);
		int nLayers = this.readShort();

		if (nLayers > 0) {
			L.d("number of layers", nLayers);
			// layers = new LayerInfo[nLayers];
			this.read_layers_info_positive(nLayers, layers);
		} else if (nLayers < 0) {
			L.d("number of layers", nLayers);
			nLayers = -nLayers;
			// layers = new LayerInfo[nLayers];
			this.read_layers_info_positive(nLayers, layers);
		} else {
			L.d("number of layers", nLayers);
		}

	}

	private boolean layer_is_ok (final LayerInfo info) {
		return info.w > 0 && info.h > 0;
	}

	private void read_layers_info_positive (final int nLayers, final Vector<LayerInfo> layers) {
		// TODO Auto-generated method stub
		for (int i = 0; i < nLayers; i++) {
			final LayerInfo info = new LayerInfo(i);
			layers.add(info);
			info.y = this.readInt();
			info.x = this.readInt();
			info.h = this.readInt() - info.y;
			info.w = this.readInt() - info.x;
			final short number_of_channels = this.readShort();

			for (int j = 0; j < number_of_channels; j++) {
				final int id = this.readShort();
				final ChannelInfo channelInfo = new ChannelInfo();
				final ChannelID channel_id = ChannelID.valueOfInt(id);
				if (!channel_id.isOK()) {
					// L.d("channel_id", channel_id);
					// L.d("number_of_channels", number_of_channels);
					//
				}
				channelInfo.setChannelID(channel_id);
				final int size = this.readInt();
				channelInfo.setSize(size);
				info.getChannels().add(channelInfo);

			}
			final String s = this.readString(4);
			if (!s.equals("8BIM")) {
				this.setStatus(Status.STATUS_FORMAT_ERROR);
				return;
			}
			// skipBytes(4); // blend mode
			final int blend_mode = this.readInt();
			info.setBlendMode(blend_mode);

			final PSD_BLEND_MODE mode_name = PSDLayerImpl.modeOf(blend_mode);
			if (mode_name == PSD_BLEND_MODE.UNKNOWN) {
				L.e("PSD_BLEND_MODE.UNKNOWN", blend_mode);
			}

			info.layerTransparency = this.readByte();
			final int clipping = this.readByte();
			final int flags = this.readByte();
			final String binary = Integer.toBinaryString(0xf0 | flags);
			// L.d("binary", binary);
			if (binary.charAt(8 - 1 - 1) != '0') {
				info.setVisible(false);
			} else {
				info.setVisible(true);
			}

			final int filter = this.readByte(); // filler
			// Log.d("clipping", clipping);
			// Log.d("flags", flags);
			// Log.d("filter", filter);
			// Log.d("blend_mode", blend_mode);

			int extraSize = this.readInt();

			// skipBytes(extraSize);

			final int mask_data_size = this.readInt();
			// Log.d("mask_data_size", mask_data_size);
			final MaskData mask_data = this.readMaskData(mask_data_size, info);
			// skipBytes(mask_data_size);
			// L.d("mask_data", mask_data);
			extraSize = extraSize - mask_data_size - 4;

			info.setMaskData(mask_data);
			//
			final int blending_ranges_data_size = this.readInt();
			// Log.d("blending_ranges_data_size", blending_ranges_data_size);
			final BlendingRanges blending_ranges_data = this.readBlendingRanges(blending_ranges_data_size);
			// L.d("blending_ranges_data", blending_ranges_data);
			info.setBlendingRanges(blending_ranges_data);
			extraSize = extraSize - blending_ranges_data_size - 4;
			final int layer_name_string_len = this.readByte();
			// Log.d("layer_name_string_len", layer_name_string_len);
			extraSize = extraSize - 1;

			final String layer_name_string = this.readString(extraSize).substring(0, layer_name_string_len);// MAX
			// 32
			// CHARS!!!!

			// if (layer_name_string.contains("animation=")) {
			// L.d("name", layer_name_string);
			// }
			info.setName(layer_name_string);

			;
			// L.d("layer read ", info);

		}

	}

	private BlendingRanges readBlendingRanges (final int len) {
		final BlendingRanges result = new BlendingRanges();
		for (int i = 0; i < len; i++) {
			final int b = (byte)this.readByte();
			result.addByte(b);
		}

		return result;
	}

	private MaskData readMaskData (final int len, final LayerInfo info) {
		final MaskData result = new MaskData(len);
		if (len == 0) {
			return result;
		}
		final String msg = "PSD-file contains mask! " + info;
		if (this.crash_on_mask) {
			throw new Error(msg);
		} else {
			L.e(msg);
		}

		int skip = len;

		final int mask_top = this.readInt();
		final int mask_left = this.readInt();
		final int mask_bottom = this.readInt();
		final int mask_right = this.readInt();
		skip = skip - 4 * 4;
		// L.d(" mask_top", mask_top);
		// L.d(" mask_left", mask_left);
		// L.d(" mask_bottom", mask_bottom);
		// L.d(" mask_right", mask_right);

		result.y = mask_top;
		result.x = mask_left;
		result.h = mask_bottom - result.y;
		result.w = mask_right - result.x;

		final int color = (byte)this.readByte();
		// L.d(" mask_color", color);

		final int flags = (byte)this.readByte();

		skip = skip - 2;
		this.skip(skip);
		// String binary = Integer.toBinaryString(0xf0 | flags);
		// //
		// L.d(" flags", binary);
		// boolean user_vector_mask = false;
		// if (binary.charAt(8 - 1 - 4) != '0') {
		// user_vector_mask = true;
		// }
		// if (user_vector_mask) {
		// int params = (byte) readByte();
		// binary = Integer.toBinaryString(0xf0 | params);
		// L.d("params", binary);
		// }
		return result;
	}

	private void skip (final int skip) {
		for (int i = 0; i < skip; i++) {
			this.readByte();
		}
	}

	protected void readLayers (final Vector<LayerInfo> layers) {
		// read and convert each layer to BufferedImage
		// frameCount = this.layers.size();
		this.content = new FileContent();

		final Vector<LayerGroup> layer_group_stack = new Vector<LayerGroup>();
		final LayerGroup root_layer_group = this.content.layers_structure.getRoot();
		layer_group_stack.add(root_layer_group);

		// frames = new BufferedImage[this.layers.size()];
		for (int i = 0; i < layers.size(); i++) {
			final LayerInfo info = layers.get(i);
			// L.d("processing", info);
			// L.d("layer", info);

			// L.d(" blend", info.getBlendingRanges());
			byte[] r = null, g = null, b = null, a = null;
			for (int j = 0; j < info.getChannels().size(); j++) {
				final ChannelInfo channel_info = info.getChannels().get(j);
				// L.d(" ", channel_info);
				switch (channel_info.getChannelID()) {
				case RED:
					r = this.readPlane(info.w, info.h, channel_info);
					break;
				case GREEN:
					g = this.readPlane(info.w, info.h, channel_info);
					break;
				case BLUE:
					b = this.readPlane(info.w, info.h, channel_info);
					break;
				case ALPHA: {
					a = this.readPlane(info.w, info.h, channel_info);
					break;
				}
				case USER_MASK: {
					final MaskData mask = info.getMaskData();
					// L.d(" ", mask);
					this.readPlane(mask.w, mask.h, channel_info);
					break;
				}
				default:
					L.d("processing", info);
					L.d("       ", channel_info);
					throw new Error();
				}
				if (this.err()) {

					L.d("LayerInfo", info);
					break;
				}
			}
			if (this.err()) {
				break;
			}
			final int n = info.w * info.h;
			if (r == null) {
				r = this.fillBytes(n, 0);
			}
			if (g == null) {
				g = this.fillBytes(n, 0);
			}
			if (b == null) {
				b = this.fillBytes(n, 0);
			}
			if (a == null) {
				a = this.fillBytes(n, 255);
			}
			if (this.layer_is_ok(info)) {
				final BufferedImage im = this.makeImage(info.w, info.h, r, g, b, a);
				// frames[i] = im;

				// begin raster;
				final RasterLayer raster_layer = new RasterLayer();

				final String name = info.getName();

				raster_layer.setName(name);
				final double offset_x = info.getX();
				final double offset_y = info.getY();
				raster_layer.getOffset().setX(offset_x);
				raster_layer.getOffset().setY(offset_y);
				raster_layer.setRaster(im);
				final float opacity = info.layerTransparency / 255f;
				raster_layer.setOpacity(opacity);
				raster_layer.setVisible(info.isVisible());

				raster_layer.setMode(info.getBlendMode());

				final LayerGroup current_group = layer_group_stack.get(0);
				this.content.raster_layers_list.add(raster_layer);
				this.content.all_layers_list.add(raster_layer);
				current_group.getSublayers().add(raster_layer);

				final int prefix = layer_group_stack.size();
				// L.d(prefix(prefix + 1) + "current_group",
				// current_group.getName());
				// L.d(prefix(prefix + 1) + " add",
				// raster_layer.getName());

			} else {
				// L.d("info", info);
				final String layer_name = info.getName();
				final int prefix = layer_group_stack.size();
				// L.d(prefix(prefix) + "layer_name", layer_name);
				if (layer_name.toLowerCase().equals("</Layer group>".toLowerCase())) {
					// begin group;
					final LayerGroup next = new LayerGroup();
					next.setName(layer_name);
					final LayerGroup current = layer_group_stack.get(0);
					this.content.all_layers_list.add(next);
					current.getSublayers().add(next);
					// L.d(prefix(prefix) + "current[", current.getName());
					// L.d(prefix(prefix + 1) + "step down", next.getName());
					layer_group_stack.insertElementAt(next, 0);
				} else {
					// end group;
					final LayerGroup current = layer_group_stack.remove(0);
					current.setName(layer_name);
					current.setVisible(info.isVisible());

					// L.d(prefix(prefix) + "closing group",
					// current.getName());

					final LayerGroup parent = layer_group_stack.get(0);
					// L.d(prefix(prefix - 1) + "current]", parent.getName());
				}
			}
		}
		this.lineLengths = null;
		if ((this.layerMaskSectionLen > 0) && !this.err()) {
			final int n = this.readInt(); // global layer mask info len
			this.skipBytes(n);
		}

		if (layer_group_stack.get(0) != root_layer_group) {
			throw new Error("Stack Corrupted! " + layer_group_stack);

		}

	}

	private String prefix (final int prefix) {
		String result = "";
		for (int i = 0; i < prefix; i++) {
			result = result + "--";
		}

		return result;
	}

	protected byte[] readPlane (final int w, final int h, final ChannelInfo channel_info) {
		// read a single color plane
		byte[] b = null;
		final int size = w * h;

		final int channel_size = channel_info.getSize();
		int skip = 0;
		int compression = 888;
		// Log.d("hasLayers", hasLayers);
		if (this.hasLayers) {
			compression = this.readShort();
			// get RLE compression info for channel
			this.rleEncoded = compression == 1;
			if (this.rleEncoded) {
				final int nLines = h;
				// list of encoded line lengths
				// L.d("RLE lines", nLines);
				this.lineLengths = new short[nLines];
				for (int i = 0; i < nLines; i++) {
					this.lineLengths[i] = this.readShort();
					// L.d(" [" + i + "]", lineLengths[i]);
				}
				skip = nLines * 2;
				this.lineIndex = 0;
			}
		}

		if (this.rleEncoded) {
			// L.d("W x H", size);
			// L.d("channel_size ", channel_size - 2 - skip);

			// int len_bytes = -1;
			b = this.readPlaneCompressed(w, h);
			// L.d("channel_data_size ", channel_size - 2 - skip - len_bytes);
		} else {
			//
			b = new byte[size];
			this.readBytes(b, size);
		}

		return b;

	}

	protected byte[] readPlaneCompressed (final int w, final int h) {
		final byte[] result = new byte[w * h];
		final byte[] temp = new byte[w * 2];
		int pos = 0;

		for (int i = 0; i < h; i++) {
			if (this.lineIndex >= this.lineLengths.length) {
				this.setStatus(Status.STATUS_FORMAT_ERROR);
				return null;
			}
			final int len = this.lineLengths[this.lineIndex++];
			this.readBytes(temp, len);
			this.decodeRLE(temp, len, result, pos);
			pos = pos + w;
		}
		return result;
	}

	protected int decodeRLE (final byte[] input, final int input_len, final byte[] output, int putput_pos) {

		int len_bytes = 0;
		final int max = input_len;
		int input_i = 0;
		while (input_i < max) {

			byte readByte = input[input_i++];

			int len = readByte;
			len_bytes++;

			if (len < 0) {
				// dup next byte 1-n times
				len = 1 - len;
				readByte = input[input_i++];
				for (int i = 0; i < len; i++) {
					output[putput_pos++] = readByte;
				}
			} else {
				// copy next n+1 bytes
				len = len + 1;
				System.arraycopy(input, input_i, output, putput_pos, len);
				putput_pos += len;
				input_i += len;
			}
		}
		// L.d("len_bytes", len_bytes);
		return len_bytes;
	}

	protected short readShort () {
		// read big-endian 16-bit integer
		return (short)((this.readByte() << 8) | this.readByte());
	}

	protected String readString (final int len) {
		// read string of specified length
		final StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {

			final byte[] bytes = new byte[] {(byte)this.readByte()};
			decodeCp1251(bytes, sb);

		}
		return sb.toString();
	}

	protected void skipBytes (final int n) {
		// skip over n input bytes
		for (int i = 0; i < n; i++) {
			this.readByte();
		}
	}

	static void decodeCp1251 (final byte[] data, final StringBuilder sb) {
		if (data == null) {
			throw new IllegalArgumentException("Null argument");
		}

		for (int i = 0; i < data.length; i++) {
			sb.append(cp1251Map[data[i] & 0xFF]);
		}

	}

	static char[] cp1251Map = new char[] {'\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007', '\u0008',
		'\u0009', '\n', '\u000B', '\u000C', '\r', '\u000E', '\u000F', '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015',
		'\u0016', '\u0017', '\u0018', '\u0019', '\u001A', '\u001B', '\u001C', '\u001D', '\u001E', '\u001F', '\u0020', '\u0021',
		'\u0022', '\u0023', '\u0024', '\u0025', '\u0026', '\'', '\u0028', '\u0029', '\u002A', '\u002B', '\u002C', '\u002D',
		'\u002E', '\u002F', '\u0030', '\u0031', '\u0032', '\u0033', '\u0034', '\u0035', '\u0036', '\u0037', '\u0038', '\u0039',
		'\u003A', '\u003B', '\u003C', '\u003D', '\u003E', '\u003F', '\u0040', '\u0041', '\u0042', '\u0043', '\u0044', '\u0045',
		'\u0046', '\u0047', '\u0048', '\u0049', '\u004A', '\u004B', '\u004C', '\u004D', '\u004E', '\u004F', '\u0050', '\u0051',
		'\u0052', '\u0053', '\u0054', '\u0055', '\u0056', '\u0057', '\u0058', '\u0059', '\u005A', '\u005B', '\\', '\u005D',
		'\u005E', '\u005F', '\u0060', '\u0061', '\u0062', '\u0063', '\u0064', '\u0065', '\u0066', '\u0067', '\u0068', '\u0069',
		'\u006A', '\u006B', '\u006C', '\u006D', '\u006E', '\u006F', '\u0070', '\u0071', '\u0072', '\u0073', '\u0074', '\u0075',
		'\u0076', '\u0077', '\u0078', '\u0079', '\u007A', '\u007B', '\u007C', '\u007D', '\u007E', '\u007F', '\u0402', '\u0403',
		'\u201A', '\u0453', '\u201E', '\u2026', '\u2020', '\u2021', '\u20AC', '\u2030', '\u0409', '\u2039', '\u040A', '\u040C',
		'\u040B', '\u040F', '\u0452', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014', '\uFFFD', '\u2122',
		'\u0459', '\u203A', '\u045A', '\u045C', '\u045B', '\u045F', '\u00A0', '\u040E', '\u045E', '\u0408', '\u00A4', '\u0490',
		'\u00A6', '\u00A7', '\u0401', '\u00A9', '\u0404', '\u00AB', '\u00AC', '\u00AD', '\u00AE', '\u0407', '\u00B0', '\u00B1',
		'\u0406', '\u0456', '\u0491', '\u00B5', '\u00B6', '\u00B7', '\u0451', '\u2116', '\u0454', '\u00BB', '\u0458', '\u0405',
		'\u0455', '\u0457', '\u0410', '\u0411', '\u0412', '\u0413', '\u0414', '\u0415', '\u0416', '\u0417', '\u0418', '\u0419',
		'\u041A', '\u041B', '\u041C', '\u041D', '\u041E', '\u041F', '\u0420', '\u0421', '\u0422', '\u0423', '\u0424', '\u0425',
		'\u0426', '\u0427', '\u0428', '\u0429', '\u042A', '\u042B', '\u042C', '\u042D', '\u042E', '\u042F', '\u0430', '\u0431',
		'\u0432', '\u0433', '\u0434', '\u0435', '\u0436', '\u0437', '\u0438', '\u0439', '\u043A', '\u043B', '\u043C', '\u043D',
		'\u043E', '\u043F', '\u0440', '\u0441', '\u0442', '\u0443', '\u0444', '\u0445', '\u0446', '\u0447', '\u0448', '\u0449',
		'\u044A', '\u044B', '\u044C', '\u044D', '\u044E', '\u044F'};

	public void setCrashOnMask (final boolean crash_on_mask) {
		this.crash_on_mask = crash_on_mask;
	}

}
