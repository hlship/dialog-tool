<script>
    import { getContext, createEventDispatcher } from "svelte";
    import { postApi, updateStoreMap } from "./common.js";
    import { deriveChildren } from "./derived.js";
    import { Button } from "flowbite-svelte";

    const dispatcher = createEventDispatcher();

    // We don't instantiate a Knot until after we have at least one knot.
    const globals = getContext("globals");
    const knots = globals.knots;
    const selected = globals.selected;

    export let id = undefined;

    // Make sure bg- and border- version of these are in the safelist in tailwind.config.js

    function computeKnotColor(knot) {
        if (knot.unblessed && knot.response == undefined) {
            return "yellow-200";
        }

        if (knot.unblessed) {
            // An actual conflict
            return "rose-400";
        }

        return "stone-200";
    }

    $: knot = $knots.get(id) || {};
    $: label = knot.label || knot.command;
    $: blessEnabled = knot.unblessed && knot.unblessed != "";
    $: color = computeKnotColor(knot);

    $: children = deriveChildren(globals.knotCommands, knot);
    $: selectedId = $selected.get(id);

    let commandField;
    let blessVisible = false;

    async function post(payload) {
        let result = await postApi(payload);

        dispatcher("result", result);

        return result;
    }

    function bless() {
        post({ action: "bless", id: id });
    }

    function replay() {
        post({ action: "replay", id: id });
    }

    let newCommand = null;

    function setSelectedId(selectedId) {
        updateStoreMap(selected, (_selected) => {
            _selected.set(id, selectedId);
        });
    }

    async function runNewCommand() {
        const result = await post({
            action: "new-command",
            command: newCommand,
            id: id,
        });

        newCommand = null;

        setSelectedId(result.new_id);
    }

    async function deleteNode() {
        post({ action: "delete", id: id });
    }
</script>

<div class="flex flex-row bg-{color} rounded-t-lg p-2 text-sm">
    <div class="mx-2 my-auto font-bold text-emerald-400">{label}</div>
    <Button size="xs" color="blue" on:click={replay}>Replay</Button>
    <!-- TODO: Make this red, but don't need a modal, because we have undo! -->
    {#if id != 0}
        <Button class="ml-2" size="xs" color="blue" on:click={deleteNode}
            >Delete</Button
        >
    {/if}
</div>

<div class="flex flex-row border-{color} border-2">
    <div class="bg-yellow-50 basis-6/12 mr-2 p-1 whitespace-pre">
        {#if knot.response}
            {knot.response}
        {:else}
            <em>No blessed response</em>
        {/if}
    </div>
    {#if blessEnabled}
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <div
            class="bg-yellow-100 basis-6/12 p-1 relative whitespace-pre"
            on:mouseenter={() => (blessVisible = true)}
            on:mouseleave={() => (blessVisible = false)}
        >
            {#if blessVisible}
                <div class="absolute top-2 right-2">
                    <Button color="blue" size="xs" on:click={bless}
                        >Bless</Button
                    >
                </div>
            {/if}
            {knot.unblessed}
        </div>
    {/if}
</div>

<div
    class="flex flex-wrap bg-{color} rounded-b-lg p-2 mb-2 text-nowrap drop-shadow-md"
>
    {#each $children as child (child.id)}
        <Button
            class="m-1"
            pill
            color={selectedId == child.id ? "green" : "blue"}
            size="xs"
            on:click={() => setSelectedId(child.id)}
            >{child.label}
        </Button>
    {/each}
    <input
        type="text"
        bind:this={commandField}
        class="ml-2 w-1/4 grow px-2 text-sm"
        placeholder="New command"
        bind:value={newCommand}
        on:change={runNewCommand}
    />
</div>
