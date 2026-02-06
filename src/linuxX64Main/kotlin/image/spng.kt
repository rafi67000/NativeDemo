package image

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.linux.malloc
import spng.SPNG_DECODE_GAMMA
import spng.SPNG_DECODE_TRNS
import spng.SPNG_FMT_RGBA8
import spng.fclose
import spng.fopen
import spng.spng_ctx_free
import spng.spng_ctx_new
import spng.spng_decode_image
import spng.spng_decoded_image_size
import spng.spng_get_ihdr
import spng.spng_ihdr
import spng.spng_set_png_file
import spng.spng_strerror

// Wczytanie PNG do RGBA (jak wcześniej)
@OptIn(ExperimentalForeignApi::class)
fun loadPng(path: String): Triple<Int, Int, CPointer<out CPointed>> = memScoped {
	val file = fopen(path, "rb") ?: error("Cannot open file")
	defer { fclose(file) }

	val ctx = spng_ctx_new(0) ?: error("spng_ctx_new failed")
	defer { spng_ctx_free(ctx) }

	spng_set_png_file(ctx, file)

	val ihdr = alloc<spng_ihdr>()
	spng_get_ihdr(ctx, ihdr.ptr)

	val width = ihdr.width.toInt()
	val height = ihdr.height.toInt()

	val outSize = alloc(1uL)

	spng_decoded_image_size(ctx, SPNG_FMT_RGBA8.toInt(), outSize.ptr)

	//        spng_set_image_limits(ctx, 2_097_152u, 2_097_152u)
	//        // 1 GB PNG file ought to be enough for anyon
	//
	//        spng_set_chunk_limits(ctx, (1 shl 30).toULong(), (1 shl 30).toULong())

	val pixels = malloc(outSize.value)!!

	val ret =
		spng_decode_image(
			ctx,
			pixels,
			outSize.value,
			SPNG_FMT_RGBA8.toInt(),
			(SPNG_DECODE_TRNS or SPNG_DECODE_GAMMA).toInt(),
		)
	if (ret != 0) error("spng_decode_image failed: $ret \n" + spng_strerror(ret)?.toKString())

	Triple(width, height, pixels)
}
