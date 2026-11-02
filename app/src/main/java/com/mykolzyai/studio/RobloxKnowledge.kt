package com.mykolzyai.studio

/**
 * Стартовая встроенная база знаний Kolzai по Roblox Studio.
 *
 * Это справочные материалы и шаблоны инструкций, а не обучение весов модели.
 * Чат сможет передавать эти материалы подключённой AI-модели как контекст.
 */
object RobloxKnowledge {
    data class Lesson(
        val id: String,
        val titleRu: String,
        val titleEn: String,
        val explanationRu: String,
        val exampleLuau: String,
        val buildingTipRu: String
    )

    val lessons: List<Lesson> = listOf(
        Lesson(
            id = "luau_variables",
            titleRu = "Переменные",
            titleEn = "Variables",
            explanationRu = "Переменная хранит значение. local объявляет локальную переменную. " +
                "Строки записываются в кавычках, числа — без кавычек.",
            exampleLuau = """
                local playerName = "Alex"
                local coins = 100
                print(playerName, coins)
            """.trimIndent(),
            buildingTipRu = "Давай объектам понятные имена: HouseWall, MainDoor, SpawnPlatform."
        ),
        Lesson(
            id = "luau_conditions",
            titleRu = "Условия",
            titleEn = "If / Else",
            explanationRu = "Условие if выполняет код, когда выражение истинно. " +
                "Для альтернативы используется elseif или else.",
            exampleLuau = """
                local coins = 120

                if coins >= 100 then
                    print("Можно купить предмет")
                else
                    print("Недостаточно монет")
                end
            """.trimIndent(),
            buildingTipRu = "Условия подходят для кнопок покупки, дверей с ключом и проверки ресурсов."
        ),
        Lesson(
            id = "luau_functions",
            titleRu = "Функции",
            titleEn = "Functions",
            explanationRu = "Функция объединяет действия в повторно используемый блок кода.",
            exampleLuau = """
                local function greetPlayer(playerName)
                    print("Привет, " .. playerName .. "!")
                end

                greetPlayer("Alex")
            """.trimIndent(),
            buildingTipRu = "Создавай отдельные функции для повторяющихся действий: BuildWall, PlaceWindow."
        ),
        Lesson(
            id = "part_basics",
            titleRu = "Создание детали",
            titleEn = "Part Basics",
            explanationRu = "Part — базовая деталь Roblox. Size задаёт размеры, Position — положение, " +
                "Anchored закрепляет деталь, Parent помещает её в Workspace.",
            exampleLuau = """
                local part = Instance.new("Part")
                part.Name = "KolzaiBlock"
                part.Size = Vector3.new(8, 2, 8)
                part.Position = Vector3.new(0, 5, 0)
                part.Anchored = true
                part.Color = Color3.fromRGB(70, 140, 255)
                part.Parent = workspace
            """.trimIndent(),
            buildingTipRu = "Сначала закрепляй архитектурные детали через Anchored = true, чтобы они не падали."
        ),
        Lesson(
            id = "part_materials",
            titleRu = "Материалы и цвет",
            titleEn = "Materials and Color",
            explanationRu = "Color3.fromRGB задаёт цвет по каналам красный, зелёный и синий. " +
                "Material задаёт визуальный материал детали.",
            exampleLuau = """
                local part = workspace:WaitForChild("KolzaiBlock")
                part.Color = Color3.fromRGB(40, 180, 120)
                part.Material = Enum.Material.Metal
            """.trimIndent(),
            buildingTipRu = "Используй небольшую согласованную палитру цветов и подходящие материалы для одного здания."
        ),
        Lesson(
            id = "models",
            titleRu = "Модели",
            titleEn = "Models",
            explanationRu = "Model группирует несколько объектов. Это удобно для домов, мебели и наборов деталей.",
            exampleLuau = """
                local model = Instance.new("Model")
                model.Name = "SimpleHouse"

                local wall = Instance.new("Part")
                wall.Name = "Wall"
                wall.Size = Vector3.new(12, 8, 1)
                wall.Anchored = true
                wall.Parent = model

                model.Parent = workspace
            """.trimIndent(),
            buildingTipRu = "Группируй части одного строения в Model и давай модели понятное имя."
        ),
        Lesson(
            id = "script_types",
            titleRu = "Типы скриптов",
            titleEn = "Script Types",
            explanationRu = "Script выполняет серверную логику, LocalScript — клиентский код в поддерживаемых контейнерах, ModuleScript подключается через require. LocalScript в ServerScriptService не выполняется.",
            exampleLuau = "-- ServerScript: ServerScriptService\n-- LocalScript: StarterPlayerScripts или StarterGui\n-- ModuleScript: повторно используемый код через require(module)",
            buildingTipRu = "Всегда указывай точный путь в Explorer и тип скрипта."
        ),
        Lesson(
            id = "remote_security",
            titleRu = "Безопасность RemoteEvent",
            titleEn = "RemoteEvent Security",
            explanationRu = "Данные клиента недоверенные. Сервер проверяет типы аргументов, диапазоны, дистанцию, права и cooldown. Клиент не должен сам назначать награду или результат покупки.",
            exampleLuau = "remote.OnServerEvent:Connect(function(player, amount)\n    if typeof(amount) ~= \"number\" then return end\n    if amount < 1 or amount > 10 then return end\n    -- Сервер проверяет игровое состояние и сам рассчитывает результат.\nend)",
            buildingTipRu = "Все важные игровые решения подтверждай сервером."
        ),
        Lesson(
            id = "nil_debugging",
            titleRu = "Исправление ошибки nil",
            titleEn = "Nil Debugging",
            explanationRu = "attempt to index nil означает, что код обращается к отсутствующему значению. Проверь путь объекта, имя, момент загрузки и результат WaitForChild.",
            exampleLuau = "local target = workspace:WaitForChild(\"TargetPart\", 10)\nif not target then\n    warn(\"TargetPart not found\")\n    return\nend\nprint(target.Name)",
            buildingTipRu = "Не маскируй ошибку случайными задержками: найди отсутствующий объект или неверный путь."
        ),
        Lesson(
            id = "model_pivot",
            titleRu = "Перемещение модели",
            titleEn = "Model Pivot",
            explanationRu = "Для перемещения целой модели используй PivotTo с CFrame. Убедись, что найденный объект действительно Model.",
            exampleLuau = "local model = workspace:WaitForChild(\"MyModel\")\nif model:IsA(\"Model\") then\n    model:PivotTo(CFrame.new(0, 5, 0))\nend",
            buildingTipRu = "Собирай дом или мебель в Model и перемещай группу целиком."
        )
    )

    /**
     * Поиск релевантных уроков. Сначала выдаёт точные совпадения,
     * затем уроки с наибольшим количеством совпавших терминов.
     */
    fun search(query: String, limit: Int = lessons.size): List<Lesson> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return lessons.take(limit.coerceAtLeast(0))

        val queryTokens = tokenize(normalized)
        if (queryTokens.isEmpty()) return emptyList()

        return lessons.mapNotNull { lesson ->
            val searchable = listOf(
                lesson.id,
                lesson.titleRu,
                lesson.titleEn,
                lesson.explanationRu,
                lesson.exampleLuau,
                lesson.buildingTipRu
            ).joinToString(" ").lowercase()

            val lessonTokens = tokenize(searchable).toSet()
            val matched = queryTokens.count { it in lessonTokens }
            val phraseBonus = if (searchable.contains(normalized)) 5 else 0
            val titleBonus = if (
                lesson.titleRu.lowercase().contains(normalized) ||
                lesson.titleEn.lowercase().contains(normalized) ||
                lesson.id.contains(normalized)
            ) 8 else 0
            val score = matched * 2 + phraseBonus + titleBonus

            if (score > 0) lesson to score else null
        }.sortedWith(
            compareByDescending<Pair<Lesson, Int>> { it.second }
                .thenBy { lessons.indexOf(it.first) }
        ).take(limit.coerceAtLeast(0)).map { it.first }
    }

    /**
     * Готовит компактный контекст для будущего подключения языковой модели.
     * Возвращает только релевантные уроки, чтобы не отправлять всю базу знаний.
     */
    fun contextFor(query: String, maxLessons: Int = 4): String {
        val relevant = search(query, maxLessons)
        if (relevant.isEmpty()) {
            return "Подходящих локальных материалов Roblox в базе Kolzai не найдено. " +
                "Не выдумывай API; при необходимости сверяйся с Creator Hub."
        }

        return buildString {
            appendLine("РЕЛЕВАНТНЫЕ МАТЕРИАЛЫ ROBLOX ИЗ БАЗЫ KOLZAI:")
            relevant.forEachIndexed { index, lesson ->
                appendLine()
                appendLine("${index + 1}. ${lesson.titleRu} (${lesson.titleEn})")
                appendLine(lesson.explanationRu)
                appendLine("Пример Luau:")
                appendLine(lesson.exampleLuau)
                appendLine("Совет: ${lesson.buildingTipRu}")
            }
            appendLine()
            append("Используй эти материалы как справку, но проверяй их применимость к задаче.")
        }
    }

    private fun tokenize(value: String): List<String> =
        value.lowercase()
            .split(Regex("[^\\\\p{L}\\\\p{N}_]+"))
            .filter { it.length >= 2 }

    /**
     * Инструкции для пошагового анализа задач по Roblox.
     * Это рабочий алгоритм ответа, а не скрытая цепочка мыслей модели.
     */
    fun systemGuidanceRu(): String = """
        Ты Kolzai — помощник по Roblox Studio, Luau и Building.
        Отвечай по-русски, а имена API и код оставляй на английском.

        ПОДХОД К РЕШЕНИЮ ЗАДАЧ:
        1. Определи цель пользователя и ожидаемый результат в Roblox Studio.
        2. Проверь, хватает ли данных. Если критически не хватает — задай один короткий вопрос.
        3. Разбей сложную задачу на понятные этапы и выбери самый надёжный простой вариант.
        4. Перед выдачей кода проверь синтаксис Luau, имена API, типы объектов,
           область видимости переменных, события и возможные ошибки nil.
        5. Проверь безопасность на клиенте и сервере: важные игровые решения,
           валюту и награды не доверяй только LocalScript.
        6. Укажи точное место для каждого скрипта: Script, LocalScript или ModuleScript,
           а также сервис или объект, куда его поместить.
        7. Если меняешь существующую систему, объясни, что заменить и что оставить.
        8. В конце дай короткий список проверок в Roblox Studio и возможных причин ошибок.

        ПРАВИЛА КАЧЕСТВА:
        - Используй актуальные API Roblox. Не выдумывай свойства, методы и сервисы.
        - Не называй код протестированным, если он фактически не запускался.
        - Не скрывай неизвестные моменты: отмечай предположения и проси недостающие данные.
        - Не переписывай весь проект, если достаточно безопасного небольшого изменения.
        - Перед удалением или перезаписью объектов предупреждай пользователя.
        - Для Building сначала продумай размеры, координаты, материалы, столкновения,
          Anchored, структуру Model и понятные имена объектов.
        - Для больших построек предлагай генерацию частями, чтобы избежать лишней нагрузки.
        - Не показывай пользователю внутренние скрытые рассуждения. Давай краткий вывод,
          план действий, проверяемые причины и результат.

        ФОРМАТ ОТВЕТА ДЛЯ КОДА:
        Цель → куда вставить → готовый код → как проверить → частые ошибки.
    """.trimIndent()
}
