@file:OptIn(ExperimentalForeignApi::class)

import cnames.structs.GLFWwindow
import glfw.*
import kotlinx.cinterop.*
import platform.linux.malloc
import spng.*

fun main() {
	println("Hello Linux!")
	Main.main()
}

object Main {

	fun main() {

		if (glfwInit() == 0) error("Failed to initialize GLFW")

		glfwDefaultWindowHints()

		glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)
		glfwInitHint(GLFW_DECORATED, GLFW_TRUE)

		glfwInitHint(GLFW_WAYLAND_LIBDECOR, GLFW_WAYLAND_PREFER_LIBDECOR)

		glfwInitHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API)

		val window =
			glfwCreateWindow(640, 480, "Kotlin Native GLFW", null, null)
				?: error("Failed to create GLFW window")

		glfwMakeContextCurrent(window)

		//        val icon_16x16 = loadPng("/home/rafi67000/IdeaProjects/NativeDemo/icon_16x16.png")
		val icon = loadPng("/home/rafi67000/IdeaProjects/NativeDemo/img.png")
		//        val icon_32x32 = loadPng("/home/rafi67000/IdeaProjects/NativeDemo/icon_32x32.png")

		val images = nativeHeap.allocArray<GLFWimage>(1)

		images[0].apply {
			width = icon.first
			height = icon.second
			pixels = icon.third.reinterpret()
		}

		//        images[0].apply {
		//            width = icon_16x16.first
		//            height = icon_16x16.second
		//            pixels = icon_16x16.third.reinterpret()
		//        }
		//
		//        images[1].apply {
		//            width = icon_32x32.first
		//            height = icon_32x32.second
		//            pixels = icon_32x32.third.reinterpret()
		//        }

		glfwSetWindowIcon(window, 1, images)

		glfwSetWindowCloseCallback(
			window,
			staticCFunction { window -> glfwSetWindowShouldClose(window, 1) },
		)

		glClearColor(255f, 0f, 0f, 1.0f)

		// Main loop
		while (glfwWindowShouldClose(window) == 0) {
			glClear(GL_COLOR_BUFFER_BIT.toUInt())

			handleInput(window)

			glfwSwapBuffers(window)
			glfwPollEvents()
		}

		free(icon.third)
		//        free(icon_16x16.third)
		//        free(icon_32x32.third)
		free(images)

		glfwDestroyWindow(window)
		glfwTerminate()
	}

	fun handleInput(window: CPointer<GLFWwindow>) {
		if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
			glfwSetWindowShouldClose(window, GLFW_TRUE)
	}

	// Wczytanie PNG do RGBA (jak wcześniej)
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
}
