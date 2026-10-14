-- Jaymo's flintlocks: our own animation state machine for TaCZ (tools/gen_tacz_guns.py writes this file).
-- A muzzle-loader has little to track: a held pose (and the empty lock once it's fired), one-shot actions (draw,
-- put away, reload, inspect, stock blow), the kick of each shot, and how you're moving.

local POSE_LINE, KICK_LINE, MOVE_LINE = 0, 1, 2
local POSE, LOCK, ACTION = 0, 1, 2
local MOVE = 0

local function action(context)
    return context:getTrack(POSE_LINE, ACTION)
end

local function spent(context)
    return (not context:hasBulletInBarrel()) and context:getAmmoCount() <= 0
end

local function play(context, name)
    context:runAnimation(name, action(context), false, PLAY_ONCE_STOP, 0.15)
end

-- the held pose, always under everything else
local pose = {}
function pose.entry(this, context)
    context:runAnimation("static_idle", context:getTrack(POSE_LINE, POSE), false, LOOP, 0)
end

-- loaded: hammer cocked, pan shut; spent: hammer down, frizzen thrown open, until it's loaded again
local lock = { loaded = {}, spent = {} }
function lock.loaded.update(this, context)
    if spent(context) then
        context:trigger("spent")
    end
end
function lock.loaded.transition(this, context, input)
    if input == "spent" then
        context:runAnimation("static_bolt_caught", context:getTrack(POSE_LINE, LOCK), false, LOOP, 0)
        return this.lock.spent
    end
end
function lock.spent.update(this, context)
    if not spent(context) then
        context:trigger("primed")
    end
end
function lock.spent.transition(this, context, input)
    if input == "primed" then
        context:stopAnimation(context:getTrack(POSE_LINE, LOCK))
        return this.lock.loaded
    end
end

-- the hands' one-shot actions
local hands = { holstered = {}, ready = {}, away = {} }
function hands.holstered.transition(this, context, input)
    if input == INPUT_DRAW then
        play(context, "draw")
        return this.hands.ready
    end
end
function hands.ready.transition(this, context, input)
    if input == INPUT_PUT_AWAY then
        context:runAnimation("put_away", action(context), false, PLAY_ONCE_HOLD, 0.15)
        return this.hands.away
    elseif input == INPUT_RELOAD then
        play(context, "reload_empty")
    elseif input == INPUT_INSPECT and context:getAimingProgress() < 1 then
        play(context, spent(context) and "inspect_empty" or "inspect")
    elseif input == INPUT_BAYONET_STOCK or input == INPUT_BAYONET_PUSH or input == INPUT_BAYONET_MUZZLE then
        play(context, "melee_stock")
    elseif input == INPUT_SHOOT and context:getAimingProgress() > 0 then
        context:stopAnimation(action(context))
    end
end

-- each shot kicks on a track of its own, so quick shots overlap
local kick = {}
function kick.transition(this, context, input)
    if input == INPUT_SHOOT then
        context:runAnimation("shoot", context:findIdleTrack(KICK_LINE, false), true, PLAY_ONCE_STOP, 0)
    end
end

-- moving: standing breath, walking (its pace follows your steps), running
local stride = { still = {}, walking = { name = "" }, running = { started = false } }
local function stepping(context, name, fade)
    local state = stride.walking
    local track = context:getTrack(MOVE_LINE, MOVE)
    if state.name ~= name then
        state.name = name
        context:runAnimation(name, track, true, LOOP, fade)
        context:anchorWalkDist()
    end
    if name ~= "idle" then
        context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
    end
end
function stride.still.update(this, context)
    local track = context:getTrack(MOVE_LINE, MOVE)
    if context:isStopped(track) or context:isHolding(track) then
        context:runAnimation("idle", track, true, LOOP, 0)
    end
end
function stride.still.transition(this, context, input)
    if input == INPUT_RUN and context:isStopped(action(context)) then
        return this.stride.running
    elseif input == INPUT_RUN or input == INPUT_WALK then
        return this.stride.walking
    end
end
function stride.walking.entry(this, context)
    stride.walking.name = ""
end
function stride.walking.update(this, context)
    if context:getShootCoolDown() > 0 or not context:isOnGround() then
        stepping(context, "idle", 0.4)
    elseif context:getAimingProgress() > 0.5 then
        stepping(context, "walk_aiming", 0.3)
    elseif context:isInputUp() then
        stepping(context, "walk_forward", 0.4)
    elseif context:isInputDown() then
        stepping(context, "walk_backward", 0.4)
    elseif context:isInputLeft() or context:isInputRight() then
        stepping(context, "walk_sideway", 0.4)
    end
end
function stride.walking.exit(this, context)
    context:runAnimation("idle", context:getTrack(MOVE_LINE, MOVE), true, PLAY_ONCE_HOLD, 0.4)
end
function stride.walking.transition(this, context, input)
    if input == INPUT_IDLE then
        return this.stride.still
    elseif input == INPUT_RUN and context:isStopped(action(context)) then
        return this.stride.running
    end
end
function stride.running.entry(this, context)
    stride.running.started = false
    context:runAnimation("run_start", context:getTrack(MOVE_LINE, MOVE), true, PLAY_ONCE_HOLD, 0.2)
end
function stride.running.update(this, context)
    local track = context:getTrack(MOVE_LINE, MOVE)
    if context:isHolding(track) and not stride.running.started then
        stride.running.started = true
        context:runAnimation("run", track, true, LOOP, 0.2)
        context:anchorWalkDist()
    end
    if stride.running.started then
        context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
    end
end
function stride.running.exit(this, context)
    context:runAnimation("run_end", context:getTrack(MOVE_LINE, MOVE), true, PLAY_ONCE_HOLD, 0.3)
end
function stride.running.transition(this, context, input)
    if input == INPUT_IDLE then
        return this.stride.still
    elseif input == INPUT_WALK or not context:isStopped(action(context)) then
        return this.stride.walking
    end
end

local M = { pose = pose, lock = lock, hands = hands, kick = kick, stride = stride }

function M:initialize(context)
    context:ensureTrackLineSize(3)
    context:ensureTracksAmount(POSE_LINE, 3)
    context:ensureTracksAmount(MOVE_LINE, 1)
    stride.walking.name = ""
    stride.running.started = false
end

function M:exit(context)
end

function M:states()
    return { self.pose, self.lock.loaded, self.hands.holstered, self.kick, self.stride.still }
end

return M
