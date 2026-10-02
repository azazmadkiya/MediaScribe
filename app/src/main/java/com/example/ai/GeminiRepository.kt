package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {
    private val api = GeminiClient.service

    private fun getActiveApiKey(customKey: String?): String {
        if (!customKey.isNullOrBlank() && customKey != "MY_GEMINI_API_KEY") {
            return customKey
        }
        return BuildConfig.GEMINI_API_KEY
    }

    suspend fun extractFromYouTube(url: String, targetLanguage: String, translate: Boolean, customApiKey: String?): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getActiveApiKey(customApiKey)
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("Gemini API Key is missing. Please configure a valid API key."))
            }

            val translationInstruction = if (translate) {
                "Translate the extracted lyrics or transcript into $targetLanguage."
            } else {
                "Keep the language as close to the original source as possible (or output in $targetLanguage if original language is not specified)."
            }

            val prompt = "You are an expert audio transcription and lyrics extraction assistant. " +
                    "Analyze this YouTube video link or reference: '$url'. " +
                    "Extract the song lyrics, spoken transcript, or key content accurately. " +
                    "$translationInstruction " +
                    "Provide a clean title for the content on the first line, followed by a separator '---', and then the full extracted lyrics/transcript text."

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                systemInstruction = Content(parts = listOf(Part(text = "You extract accurate song lyrics and transcriptions from YouTube video links.")))
            )

            val response = api.generateContent("gemini-3.5-flash", apiKey, request)
            val fullText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No content extracted."

            val lines = fullText.lines()
            val title = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: "YouTube Media Note"
            val content = if (lines.size > 1) lines.drop(1).joinToString("\n").trimStart('-', ' ') else fullText

            Result.success(Pair(title, content))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transcribeMedia(mimeType: String, base64Data: String, fileName: String, targetLanguage: String, translate: Boolean, customApiKey: String?): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getActiveApiKey(customApiKey)
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("Gemini API Key is missing."))
            }

            val translationInstruction = if (translate) {
                "Translate the transcription or lyrics into $targetLanguage."
            } else {
                "Transcribe in the original language spoken or sung in the file."
            }

            val prompt = "Transcribe the audio or video content of this file ($fileName) completely and accurately. " +
                    "If it's a song, write down the song lyrics with verse and chorus markers. " +
                    "$translationInstruction " +
                    "Provide a descriptive title on the first line, followed by '---', and then the transcript."

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt),
                            Part(inlineData = InlineData(mimeType = mimeType, data = base64Data))
                        )
                    )
                )
            )

            val response = api.generateContent("gemini-3.5-flash", apiKey, request)
            val fullText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No transcription generated."

            val lines = fullText.lines()
            val title = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Media Note: $fileName"
            val content = if (lines.size > 1) lines.drop(1).joinToString("\n").trimStart('-', ' ') else fullText

            Result.success(Pair(title, content))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refineWithHighThinking(currentContent: String, targetLanguage: String, translate: Boolean, customApiKey: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getActiveApiKey(customApiKey)
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("Gemini API Key is missing."))
            }

            val translationInstruction = if (translate) {
                "Translate and format the text into $targetLanguage."
            } else {
                "Keep or format the text in its original language."
            }

            val prompt = "Refine, structure, and format the following text into professional song lyrics with Verse, Chorus, Bridge tags, or well-structured meeting notes if it's speech. $translationInstruction\n\n$currentContent"

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    thinkingConfig = ThinkingConfig(thinkingLevel = "high")
                )
            )

            val response = api.generateContent("gemini-3.1-pro-preview", apiKey, request)
            val refined = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: currentContent

            Result.success(refined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
