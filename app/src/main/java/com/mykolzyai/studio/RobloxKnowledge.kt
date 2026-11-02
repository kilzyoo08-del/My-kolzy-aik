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
        )
    )

    fun search(query: String): List<Lesson> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return lessons

        return lessons.filter { lesson ->
            lesson.titleRu.lowercase().contains(normalized) ||
                lesson.titleEn.lowercase().contains(normalized) ||
                lesson.explanationRu.lowercase().contains(normalized) ||
                lesson.id.contains(normalized)
        }
    }

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
