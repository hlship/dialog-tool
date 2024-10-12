<script>
    import { getContext, createEventDispatcher, tick } from "svelte";
    import { postApi, selectChild } from "./common.js";
    import * as common from "./common.js";
    import {
        Button,
        Dropdown,
        DropdownDivider,
        DropdownItem,
        Helper,
    } from "flowbite-svelte";
    import KnotText from "./KnotText.svelte";
    import {
        DotsVerticalOutline,
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
    $: controlColor = null;

    const category2color = {
        error: { text: "text-rose-400", border: "border-rose-400", background: "bg-rose-200" },
        new: { text: "text-yellow-400", border: "border-yellow-200", background: "bg-yellow-200" },
        ok: { border: "border-green-200", background: ""}
        };

    $: {
        let knotCategory = common.category(knot);

        knotColor = category2color[knotCategory];

        blessEnabled = knotCategory != "ok";

        blessClass = blessEnabled ? null : "text-gray-600 cursor-not-allowed";

        controlColor = category2color[ $category.get(id)];
    }

    function computeChildren(knots, category, selectedId) {
        let result = [];

        for (const childId of knot.children || []) {
            let child = {
                id: childId,
                label: knots.get(childId).command,
                color: childId == selectedId ? "blue" : "green",
            };

            let childCategory = category.get(childId);

            child.iconColor = category2color[childCategory].text;

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

<div class="border-x-4 {knotColor.border}" id="knot_{id}">
    <KnotText response={knot.response} unblessed={knot.unblessed}>
        <div slot="actions" class="whitespace-normal absolute top-2 right-2 align-middle">
            {#if knot.label}
            <span class="text-bold bg-gray-200 border-1 mr-4 p-2 rounded-md">{knot.label}</span>
            {/if}
            <Button color="light" size="xs" class="w-0 {controlColor.background}"
                ><DotsVerticalOutline class="w-4 h-4" /></Button
            >
            <Dropdown
                placement="left"
                bind:open={dropdownOpen}
                class="w-96 h-72 overflow-y-auto bg-slate-100"
            >
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
                <DropdownItem on:click={() => null}>Edit Label</DropdownItem>
                <DropdownItem on:click={selectThis}>New Child</DropdownItem>
                {#if children.length > 0}
                    <DropdownDivider class="bg-black" />
                    {#each children as child (child.id)}
                        <DropdownItem on:click={() => setSelectedId(child.id)}>
                            {#if child.iconColor}
                                <ExclamationCircleSolid
                                    class="h-5 w-5 me-2 inline {child.iconColor}"
                                />
                            {/if}
                            {child.label}
                        </DropdownItem>
                    {/each}
                {/if}
            </Dropdown>
        </div>
    </KnotText>
    <hr/>
</div>
