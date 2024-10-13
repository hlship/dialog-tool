<script>
    import { getContext, createEventDispatcher } from "svelte";
    import { postApi, selectChild } from "./common.js";
    import * as common from "./common.js";
    import { Button, Dropdown, DropdownItem, Helper } from "flowbite-svelte";
    import KnotText from "./KnotText.svelte";
    import EditProperty from "./EditProperty.svelte";
    import { DotsVerticalOutline, CodeMergeSolid } from "flowbite-svelte-icons";

    const dispatcher = createEventDispatcher();

    const knots = getContext("knots");
    const selected = getContext("selected");
    const category = getContext("category");

    let blessEnabled;
    let blessClass;
    let editLabel;

    export let id = undefined;

    $: knot = $knots.get(id) || {};
    $: knotColor = null;
    $: controlColor = null;

    const category2color = {
        error: {
            border: "border-rose-400",
            background: "bg-rose-200 hover:bg-rose-400",
        },
        new: {
            border: "border-yellow-200",
            background: "bg-yellow-200 hover:bg-yellow-300",
        },
        ok: { border: "border-slate-100", background: "hover:bg-slate-200" },
    };

    $: {
        let knotCategory = common.category(knot);

        knotColor = category2color[knotCategory];

        blessEnabled = knotCategory != "ok";

        blessClass = blessEnabled ? null : "text-gray-600 cursor-not-allowed";

        controlColor = category2color[$category.get(id)];
    }

    function computeChildren(knots, category) {
        let result = [];

        for (const childId of knot.children || []) {
            let child = {
                id: childId,
                label: knots.get(childId).command,
            };

            let navCategory = category.get(childId);

            child.navColor = category2color[navCategory];

            result.push(child);
        }

        return result;
    }

    $: children = computeChildren($knots, $category);

    let actionDropdownOpen;
    let childDropdownOpen;

    async function post(payload) {
        actionDropdownOpen = false;

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
        actionDropdownOpen = false;
        childDropdownOpen = false;
        selectChild(selected, id, selectedId);
    }

    function deleteKnot() {
        post({ action: "delete", id: id });
    }

    // Select this node as the deepest selected node; which will cause the NewCommand to apply to this knot
    function selectThis() {
        actionDropdownOpen = false;
        selectChild(selected, id, null);
        dispatcher("focusNewCommand", {});
    }

    function startEditLabel() {
        actionDropdownOpen = false;
        editLabel.activate();
    }

    function onEditLabelComplete(e) {
        post({ action: "label", id: id, label: e.detail });
    }
</script>

<div class="border-x-4 {knotColor.border}" id="knot_{id}">
    <KnotText response={knot.response} unblessed={knot.unblessed}>
        <div
            slot="actions"
            class="whitespace-normal flex flex-row absolute top-2 right-2 gap-x-2"
        >
            {#if knot.label}
                <span class="text-bold bg-gray-200 border-1 p-1 rounded-md">{knot.label}</span>
            {/if}
            <Button
                color="light"
                size="xs"
                class="w-0 {controlColor.background}"
                ><DotsVerticalOutline class="w-4 h-4" />
            </Button>
            <Dropdown
                placement="left"
                bind:open={actionDropdownOpen}
                class="w-96 bg-slate-100"
            >
                <DropdownItem on:click={replay} class="hover:bg-slate-200"
                    >Replay</DropdownItem
                >
                {#if id != 0}
                    <DropdownItem
                        on:click={deleteKnot}
                        class="hover:bg-slate-200"
                    >
                        Delete
                        <Helper>Delete this knot and all children</Helper>
                    </DropdownItem>
                {/if}
                <DropdownItem
                    on:click={bless}
                    class="{blessClass} hover:bg-slate-200"
                >
                    Bless
                    <Helper>Accept changes</Helper>
                </DropdownItem>
                {#if id != 0}
                    <DropdownItem
                        on:click={blessTo}
                        class="{blessClass} hover:bg-slate-200"
                    >
                        Bless To Here
                        <Helper>Bless all knots from root to here</Helper>
                    </DropdownItem>
                {/if}
                <DropdownItem
                    on:click={startEditLabel}
                    class="hover:bg-slate-200"
                >
                    Edit Label
                    <Helper>Change label for knot</Helper>
                </DropdownItem>
                <DropdownItem on:click={selectThis} class="hover:bg-slate-200">
                    New Child
                    <Helper>Add a new command after this</Helper>
                </DropdownItem>
            </Dropdown>
            {#if children.length > 0}
                <Button
                    class="w-0 {controlColor.background}"
                    color="light"
                    size="xs"
                >
                    <CodeMergeSolid class="w-4 h-4" />
                </Button>
                <Dropdown
                    placement="left"
                    bind:open={childDropdownOpen}
                    class="w-96 overflow-y-auto bg-slate-100"
                >
                    {#each children as child (child.id)}
                        <DropdownItem
                            on:click={() => setSelectedId(child.id)}
                            class={child.navColor.background}
                        >
                            {child.label}
                        </DropdownItem>
                    {/each}
                </Dropdown>
            {/if}
        </div>
    </KnotText>
    <hr />
</div>

<EditProperty
    title="Edit Label"
    on:change={onEditLabelComplete}
    value={knot.label}
    bind:this={editLabel}
/>
