<script>
    import { getContext, createEventDispatcher, tick } from "svelte";
    import { postApi, selectChild } from "./common.js";
    import * as common from "./common.js";
    import { Button, Tooltip } from "flowbite-svelte";
    import { KeyboardSolid} from "flowbite-svelte-icons";
 
    import KnotText from "./KnotText.svelte";
    import {
        CloseCircleSolid,
        PlaySolid,
        PenOutline,
        ExclamationCircleSolid,
    } from "flowbite-svelte-icons";
    import NewCommand from "./NewCommand.svelte";

    const dispatcher = createEventDispatcher();

    const knots = getContext("knots");
    const selected = getContext("selected");
    const category = getContext("category");

    export let id = undefined;

    $: knot = $knots.get(id) || {};
    $: label = knot.label || knot.command;
    $: selectedId = $selected.get(id);

    $: knotColor = null;

    // Make sure bg- and border- version of these are in the safelist in tailwind.config.js

    const category2color = {
        error: "rose-400",
        new: "yellow-200",
        ok: "stone-200",
    };

    $: {
        let knotcategory = common.category(knot);

        knotColor = category2color[knotcategory];
    }

    function computeChildren(knots, category, selectedId) {

        let result = [];
        
        for (const childId of knot.children || []) {
            let child = {
                id: childId,
                label: knots.get(childId).command,
                color: childId == selectedId ? "blue" : "green",
            };

            let childcategory = category.get(childId);

            if (childcategory == "new") {
                child.iconColor = "yellow";
            }
            if (childcategory == "error") {
                child.iconColor = "red";
            }

            result.push(child);
        }

        return result;
    }

    $: children = computeChildren($knots, $category, selectedId);

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


    function setSelectedId(selectedId) {
        selectChild(selected, id, selectedId);
    }

    function deleteNode() {
        post({ action: "delete", id: id });
    }

    // Select this node as the deepest selected node; which will cause the NewCommand to apply to this node
    // Ultimately, want to be able to force focus into the newCommand text field
    function selectThis() {
            selectChild(selected, id, null);
            dispatcher("focusNewCommand", {});
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
    id="knot_{id}"
    class="flex w-full justify-between items-center bg-{knotColor} rounded-t-lg p-2 text-sm"
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
        <div class="flex items-center text-nowrap w-full">
            <div
                class="ml-4 mr-10 font-bold basis-1/4 text-ellipsis overflow-hidden"
            >
                {label}
            </div>
            {#if id != 0}
                <Button size="xs" color="blue" on:click={startLabelEdit}>
                    <PenOutline class="w-5 h-5 me-2" />Edit Label
                </Button>
            {/if}
        </div>
    {/if}
    <div class="flex space-x-2 w-full">
        <Button size="xs" color="blue" on:click={selectThis}>
            <KeyboardSolid class="w-5 h-5 me-2"/>
            New Child
        </Button>
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

<div class="flex flex-row border-{knotColor} border-2">
    <KnotText
        response={knot.response}
        unblessed={knot.unblessed}
        on:bless={bless}
        on:blessTo={blessTo}
    />
</div>

<div
    class="flex flex-wrap bg-{knotColor} rounded-b-lg p-2 mb-2 text-nowrap drop-shadow-md"
>
    {#each children as child (child.id)}
        <Button
            class="m-1 max-w-64"
            pill
            color={child.color}
            size="xs"
            on:click={() => setSelectedId(child.id)}
        >
            {#if child.iconColor}
                <ExclamationCircleSolid
                    color={child.iconColor}
                    class="h-5 w-5 me-2"
                />
            {/if}
            <span class="text-ellipsis overflow-hidden">{child.label}</span>
        </Button>
    {/each}
</div>
