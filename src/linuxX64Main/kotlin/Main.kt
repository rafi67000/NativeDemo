@file:OptIn(ExperimentalForeignApi::class)

import cnames.structs.GLFWwindow
import glfw.*
import image.loadPng
import kotlinx.cinterop.*
import spng.*

fun main() {
	println("Hello Linux!")
	Main.main()
}

object Main {

	fun main() {

		glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)
		glfwInitHint(GLFW_DECORATED, GLFW_TRUE)

		glfwInitHint(GLFW_WAYLAND_LIBDECOR, GLFW_WAYLAND_PREFER_LIBDECOR)

		glfwInitHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API)

		glfwDefaultWindowHints()
		glfwWindowHintString(GLFW_WAYLAND_APP_ID, "kotlin-native-app")
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

		if (glfwInit() == 0) error("Failed to initialize GLFW")

		//                val mode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!.pointed

		val window =
			glfwCreateWindow(640, 480, "Kotlin Native GLFW", null, null)
				?: error("Failed to create GLFW window")

		glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE)

		//        val icon_16x16 = loadPng("/home/rafi67000/IdeaProjects/NativeDemo/icon_16x16.png")
		//		val icon = loadPng("img.png")
		val icon = loadPng("icon_32x32.png")
		//        val icon_32x32 = loadPng("/home/rafi67000/IdeaProjects/NativeDemo/icon_32x32.png")

		memScoped {
			val images = allocArray<GLFWimage>(1)

			images[0].apply {
				width = icon.first
				height = icon.second
				pixels = icon.third.reinterpret()
			}

			glfwSetWindowIcon(window, 1, images)
		}

		glfwSetWindowCloseCallback(
			window,
			staticCFunction { window -> glfwSetWindowShouldClose(window, 1) },
		)

        glfwSetFramebufferSizeCallback(window, staticCFunction { _, width, height ->
            glViewport(0, 0, width, height)
        })

        glfwMakeContextCurrent(window)

		// --- OpenGL init ---
		program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

		memScoped {
			val vaoPtr = alloc<UIntVar>()
			glCreateVertexArrays(1, vaoPtr.ptr)
			vao = vaoPtr.value
		}

		// --- Load image ---
		val (w, h, pixels) = loadPng("img.png")

		val texPtr = nativeHeap.alloc<UIntVar>()
		glCreateTextures(GL_TEXTURE_2D.toUInt(), 1, texPtr.ptr)
		texture = texPtr.value

		glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER.toUInt(), GL_LINEAR)
		glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER.toUInt(), GL_LINEAR)
		glTextureParameteri(texture, GL_TEXTURE_WRAP_S.toUInt(), GL_CLAMP_TO_EDGE)
		glTextureParameteri(texture, GL_TEXTURE_WRAP_T.toUInt(), GL_CLAMP_TO_EDGE)

		glTextureStorage2D(texture, 1, GL_RGBA8.toUInt(), w, h)

		glTextureSubImage2D(
			texture,
			0,
			0,
			0,
			w,
			h,
			GL_RGBA.toUInt(),
			GL_UNSIGNED_BYTE.toUInt(),
			pixels,
		)

		glClearColor(0f, 0f, 0f, 1.0f)

		// Main loop
		while (glfwWindowShouldClose(window) == 0) {
			glClear(GL_COLOR_BUFFER_BIT.toUInt())

			glUseProgram(program)
			glBindVertexArray(vao)
			glBindTextureUnit(0u, texture)

			glDrawArrays(GL_TRIANGLES.toUInt(), 0, 3)

			handleInput(window)

			glfwSwapBuffers(window)
			glfwPollEvents()
		}

		glDeleteProgram(program)
		glDeleteVertexArrays(1, cValuesOf(vao))
		glDeleteTextures(1, cValuesOf(texture))

		free(icon.third)

		free(pixels)
		free(texPtr.ptr)

		glfwDestroyWindow(window)
		glfwTerminate()
	}

	fun handleInput(window: CPointer<GLFWwindow>) {
		if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
			glfwSetWindowShouldClose(window, GLFW_TRUE)
	}

	var program: UInt = 0u
	var vao: UInt = 0u
	var texture: UInt = 0u

	// ================= SHADERS =================

	private const val VERTEX_SHADER =
		"""
#version 460 core

const vec2 verts[3] = vec2[](
	vec2(-1.0, -1.0),
	vec2( 3.0, -1.0),
	vec2(-1.0,  3.0)
);

out vec2 uv;

void main() {
	gl_Position = vec4(verts[gl_VertexID], 0.0, 1.0);
	uv = gl_Position.xy * 0.5 + 0.5;
}
"""

	private const val FRAGMENT_SHADER =
		"""
#version 460 core

layout(binding = 0) uniform sampler2D uTex;
in vec2 uv;
out vec4 color;

void main() {
	color = texture(uTex, vec2(uv.x, 1.0 - uv.y)); // flip Y
}
"""

	// ================= UTILS =================

	fun createProgram(vsSrc: String, fsSrc: String): UInt = memScoped {
		val strings = nativeHeap.allocArray<CPointerVarOf<CPointer<ByteVarOf<Byte>>>>(1)

		val vs = glCreateShader(GL_VERTEX_SHADER.toUInt())
		prepareShader(vsSrc, vs, strings)

		val fs = glCreateShader(GL_FRAGMENT_SHADER.toUInt())
		prepareShader(fsSrc, fs, strings)

		val program = glCreateProgram()
		glAttachShader(program, vs)
		glAttachShader(program, fs)
		glLinkProgram(program)

		glDeleteShader(vs)
		glDeleteShader(fs)

		return program
	}

	fun AutofreeScope.prepareShader(
		src: String,
		shader: GLuint,
		array: CArrayPointer<CPointerVarOf<CPointer<ByteVarOf<Byte>>>>,
	) {
		array[0] = src.cstr.getPointer(this)

		glShaderSource(shader, 1, array, null)
		glCompileShader(shader)
	}
}
