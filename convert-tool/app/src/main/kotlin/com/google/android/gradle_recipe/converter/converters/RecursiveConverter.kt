/*
 * Copyright 2022 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gradle_recipe.converter.converters

import com.google.android.gradle_recipe.converter.converters.RecipeConverter.Mode
import com.google.android.gradle_recipe.converter.recipe.visitRecipes
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

const val INDEX_METADATA_FILE = "README.md"

/* Recursive converter converts recipes from source of truth mode
   to release mode
 */
class RecursiveConverter(
    private val agpVersion: String?,
    private var repoLocation: String?,
    var gradleVersion: String?,
    var gradlePath: String?,
    private var overwrite: Boolean,
) {

    private val keywordsToRecipePaths = mutableMapOf<String, MutableList<Path>>()

    @Throws(IOException::class)
    fun convertAllRecipes(sourceAll: Path, destination: Path) {
        if (!sourceAll.exists()) {
            error("the source $sourceAll folder doesn't exist")
        }

        val recipeConverter = RecipeConverter(
            agpVersion = agpVersion,
            repoLocation = repoLocation,
            gradleVersion = gradleVersion,
            gradlePath = gradlePath,
            mode = Mode.RELEASE,
            overwrite = overwrite
        )

        visitRecipes(sourceAll) { recipeFolder: Path ->
            val recipeRelativeName = sourceAll.relativize(recipeFolder)
            val currentRecipeDestination = destination.resolve(recipeRelativeName)
            val conversionResult = recipeConverter.convert(
                recipeFolder,
                currentRecipeDestination
            )

            if (conversionResult.isConversionSuccessful) {
                for (keyword in conversionResult.recipe.keywords) {
                    val list = keywordsToRecipePaths.computeIfAbsent(keyword) { mutableListOf() }
                    list.add(recipeRelativeName)
                }
            }
        }

        writeRecipesIndexFile(keywordsToRecipePaths.toSortedMap(), destination)
    }

    private fun writeRecipesIndexFile(
        keywordsToRecipePaths: MutableMap<String, MutableList<Path>>,
        destination: Path,
    ) {
        val builder = StringBuilder()
        val commaDelimiter = ", "
        builder.appendLine("# Recipes Index")

        keywordsToRecipePaths.keys.forEach { indexKeyword ->
            builder.appendLine("* $indexKeyword - ")
            val joiner = StringJoiner(commaDelimiter)

            keywordsToRecipePaths[indexKeyword]?.forEach { recipeRelativePath ->
                val line =
                    "[$recipeRelativePath]($recipeRelativePath)"
                joiner.add(line)
            }

            builder.appendLine(joiner.toString())
        }

        File(
            destination.resolve(INDEX_METADATA_FILE).toUri()
        ).writeText(builder.toString())
    }
}