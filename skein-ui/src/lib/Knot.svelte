<script>
    import { getContext, createEventDispatcher, tick } from "svelte";
    import { postApi, selectChild } from "./common.js";
    import * as common from "./common.js";
    import {
        Button,
        Dropdown,
        DropdownItem,
        Helper,
    } from "flowbite-svelte";
    import KnotText from "./KnotText.svelte";
    import {
        DotsVerticalOutline,
        PenOutline,
        ExclamationCircleSolid,
    } from "flowbite-svelte-icons";

    const dispatcher = createEventDispatcher();

    const knots = getContext("knots");
    const selected = getContext("selected");
    const category = getContext("category");

    let blessEnabled;
    let blessClass;

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
        let knotCategory = common.category(knot);

        knotColor = category2color[knotCategory];

        blessEnabled = knotCategory != "ok";

        blessClass = blessEnabled ? null : "text-gray-400 cursor-not-allowed"
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

    let dropdownOpen;

    async function post(payload) {
        dropdownOpen = false;

        let result = await postApi(payload);

        dispatcher("result", result);

        return result;
    }


    function bless() {
        if (blessEnabled) {
            post({ action: "bless", id: id });
        }
    }

    function blessTo() {
        if (blessEnabled) {
            post({ action: "bless-to", id: id });
        }
    }

    function replay() {
        post({ action: "replay", id: id });
    }

    function setSelectedId(selectedId) {
        dropdownOpen = false;
        selectChild(selected, id, selectedId);
    }

    function deleteKnot() {
        post({ action: "delete", id: id });
    }

    // Select this node as the deepest selected node; which will cause the NewCommand to apply to this node
    // Ultimately, want to be able to force focus into the newCommand text field
    function selectThis() {
        dropdownOpen = false;
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
</div>

<div class="flex flex-row border-{knotColor} border-2">
    <KnotText response={knot.response} unblessed={knot.unblessed}>
        <div slot="actions" class="whitespace-normal absolute top-2 right-2">
            <Button color="light" size="xs" class="w-0"
                ><DotsVerticalOutline class="w-4 h-4" /></Button
            >
            <Dropdown bind:open={dropdownOpen} class="w-60">
                <DropdownItem on:click={replay}>Replay</DropdownItem>
                {#if id != 0}
                    <DropdownItem on:click={deleteKnot}
                        >Delete
                        <Helper>Delete this knot and all children</Helper>
                    </DropdownItem>
                {/if}
                <DropdownItem on:click={bless} class={blessClass}
                    >Bless
                    <Helper>Accept changes</Helper>
                </DropdownItem>
                {#if id != 0}
                    <DropdownItem on:click={blessTo} class={blessClass}
                        >Bless To Here
                        <Helper>Bless all knots from root to here</Helper>
                    </DropdownItem>
                {/if}
                <DropdownItem on:click={selectThis}>New Child</DropdownItem>
            </Dropdown>
        </div>
    </KnotText>
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
