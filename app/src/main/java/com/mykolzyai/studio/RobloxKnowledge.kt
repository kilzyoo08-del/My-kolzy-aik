package com.mykolzyai.studio

/**
 * Стартовая встроенная база знаний Kolzai по Roblox Studio.
 *
 * Это справочные материалы и проверенные шаблоны, а не обучение весов модели.
 * Позже чат Kolzai сможет искать подходящие разделы и передавать их AI-модели.
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

    fun systemGuidanceRu(): String = """
        Ты Kolzai — помощник по Roblox Studio, Luau и Building.
        Отвечай по-русски, а имена API и код оставляй на английском.
        Для кода указывай, куда его поместить: Script, LocalScript или ModuleScript,
        и в какой сервис или объект Roblox Studio.
        Используй актуальные API Roblox. Не выдумывай свойства и методы.
        Объясняй кратко, проверяй возможные ошибки и предупреждай перед действиями,
        которые могут удалить или перезаписать объекты проекта.
        Для строительства сначала уточняй или описывай размеры, позицию, материалы
        и структуру Model. Не утверждай, что код запущен или проверен, если он не запускался.
    """.trimIndent()
}
