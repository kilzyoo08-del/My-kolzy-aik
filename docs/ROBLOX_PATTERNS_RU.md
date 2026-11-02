# Roblox: проверенные шаблоны проектирования для Kolzai

Краткие шаблоны для подготовки решений. Перед использованием адаптируй имена объектов и проверь актуальность API в Creator Hub.

## 1. Серверный шаблон PlayerAdded
```lua
local Players = game:GetService("Players")

local function setupPlayer(player: Player)
    local leaderstats = Instance.new("Folder")
    leaderstats.Name = "leaderstats"
    leaderstats.Parent = player

    local coins = Instance.new("IntValue")
    coins.Name = "Coins"
    coins.Value = 0
    coins.Parent = leaderstats
end

Players.PlayerAdded:Connect(setupPlayer)
```
Для уже подключённых игроков в тестовых или повторно запускаемых сценариях можно дополнительно пройтись по Players:GetPlayers(), чтобы не пропустить существующих игроков.

## 2. Безопасная серверная обработка запроса
Пример показывает принцип проверки, а не готовую систему экономики:
```lua
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")

local requestEvent = ReplicatedStorage:WaitForChild("RequestAction") :: RemoteEvent
local lastRequest: {[Player]: number} = {}

requestEvent.OnServerEvent:Connect(function(player: Player, requestedAmount: any)
    if typeof(requestedAmount) ~= "number" then
        return
    end
    if requestedAmount ~= requestedAmount or requestedAmount < 1 or requestedAmount > 10 then
        return
    end

    local now = os.clock()
    local previous = lastRequest[player] or 0
    if now - previous < 1 then
        return
    end
    lastRequest[player] = now

    -- Здесь сервер самостоятельно проверяет условия и рассчитывает результат.
end)

Players.PlayerRemoving:Connect(function(player)
    lastRequest[player] = nil
end)
```
Никогда не доверяй requestedAmount как готовому значению награды; это лишь запрос, который сервер должен проверить по игровому состоянию.

## 3. Поиск объекта с тайм-аутом
```lua
local target = workspace:WaitForChild("TargetPart", 10)
if not target then
    warn("TargetPart was not found within 10 seconds")
    return
end
```
Используй тайм-аут для объектов, отсутствие которых должно приводить к понятной диагностике. Для обязательных объектов выбери явную стратегию ошибки.

## 4. Безопасное перемещение модели
```lua
local model = workspace:WaitForChild("MyModel")
if not model:IsA("Model") then
    warn("MyModel must be a Model")
    return
end

model:PivotTo(CFrame.new(0, 5, 0))
```
Проверь, что модель содержит ожидаемые детали и что перемещение не нарушает игровую логику.

## 5. Подключение ProximityPrompt
Серверный Script рядом с объектом или в ServerScriptService может обрабатывать Triggered. Всегда проверяй условия доступа на сервере:
```lua
local prompt = workspace:WaitForChild("Door"):WaitForChild("ProximityPrompt")
if not prompt:IsA("ProximityPrompt") then
    error("Expected ProximityPrompt")
end

prompt.Triggered:Connect(function(player: Player)
    -- Серверная проверка ключа, команды, дистанции и состояния двери.
    print(player.Name .. " triggered the door prompt")
end)
```

## 6. DataStore — правила надёжности
- Получай DataStore через DataStoreService на сервере.
- Используй pcall для GetAsync/UpdateAsync/SetAsync.
- Обрабатывай отказ сохранения; не сообщай игроку, что данные сохранены, пока операция не подтвердилась.
- Не делай запрос при каждом изменении значения или каждом кадре.
- Для конкурентных изменений предпочитай UpdateAsync с аккуратной функцией преобразования.
- Не включай API-доступ в основной production-проект ради удобства тестирования; используй отдельную тестовую среду.

## 7. Как Kolzai должен проверять код
До выдачи решения проверь:
1. Все имена сервисов, событий и объектов согласованы.
2. Клиентский и серверный код разделены правильно.
3. Все RemoteEvent имеют понятный контракт аргументов.
4. На сервере есть проверки аргументов и игрового состояния.
5. Подписки на события не создаются многократно без необходимости.
6. Ошибки и отсутствие объектов обрабатываются явно.
7. Инструкция по размещению совпадает с типом скрипта.
8. Есть сценарий ручной проверки в Studio.

Не называй шаблон «полностью безопасным» или «протестированным» без реального аудита и запуска.
