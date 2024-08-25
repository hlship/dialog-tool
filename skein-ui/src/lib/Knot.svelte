<script>
    import { getContext, createEventDispatcher, tick } from "svelte";
    import { postApi, updateStoreMap } from "./common.js";
    import { deriveChildren } from "./derived.js";
    import { Button, Tooltip } from "flowbite-svelte";
    import {
        CloseCircleSolid,
        CheckCircleSolid,
        CheckPlusCircleSolid,
        PlaySolid,
        PenOutline,
    } from "flowbite-svelte-icons";

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

    function blessTo() {
        post({ action: "bless-to", id: id });
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

    function deleteNode() {
        post({ action: "delete", id: id });
    }

    var edittingLabel;
    var editLabelField;
    var newLabel;

    async function startLabelEdit() {
        newLabel = label;
        edittingLabel = true;

        await tick();

        editLabelField.select();
    }

    function labelEditKeydown(event) {
        if (event.code == "Escape") {
            edittingLabel = false;
            event.preventDefault();
        }

        if (event.code == "Enter") {
            edittingLabel = false;
            event.preventDefault();

            post({ action: "label", id: id, label: newLabel });
        }
    }
</script>

<div
    class="flex w-full justify-between items-center bg-{color} rounded-t-lg p-2 text-sm"
>
    {#if edittingLabel}
        <div class="w-full me-2">
            <input
                type="text"
                bind:this={editLabelField}
                bind:value={newLabel}
                on:keydown={labelEditKeydown}
                class="mr-2 w-1/4 grow px-2 text-sm"
            />
            <Button
                size="xs"
                color="blue"
                on:click={() => (edittingLabel = false)}>Cancel</Button
            >
        </div>
    {:else}
        <div class="mx-2 font-bold text-emerald-400">
            {label}
            {#if id != 0}
                <Button size="xs" color="blue" on:click={startLabelEdit}>
                    <PenOutline class="w-5 h-5 me-2" />Edit Label
                </Button>
            {/if}
        </div>
    {/if}
    <div class="flex space-x-2">
        <Button size="xs" color="blue" on:click={replay}>
            <PlaySolid class="w-5 h-5 me-2" /> Replay</Button
        >
        <Tooltip>Replay game to here</Tooltip>
        {#if id != 0}
            <Button class="ml-2" size="xs" color="blue" on:click={deleteNode}>
                <CloseCircleSolid class="w-5 h-5 me-2" />Delete</Button
            >
            <Tooltip>Delete knot (and children)</Tooltip>
        {/if}
    </div>
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
                    <Button color="blue" size="xs" on:click={bless}>
                        <CheckCircleSolid class="w-5 h-5 me-2" /> Bless</Button
                    >
                    <Tooltip>Accept this change</Tooltip>
                    <Button color="blue" size="xs" on:click={blessTo}>
                        <CheckPlusCircleSolid class="w-5 h-5 me-2" /> Bless To</Button
                    >
                    <Tooltip>Accept all changes from start to here</Tooltip>
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
