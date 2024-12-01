<script lang="ts">
    import { category2color } from "./knot-color";
    import * as common from "./common.svelte";
    import { Button, Dropdown, DropdownItem, Helper } from "flowbite-svelte";
    import KnotText from "./KnotText.svelte";
    import EditProperty from "./EditProperty.svelte";
    import { DotsVerticalOutline, CodeMergeSolid } from "flowbite-svelte-icons";
    import { Category, type KnotNode } from "./types";

    interface Props {
        knot: KnotNode;
        processResult: (result: common.ActionResult) => void;
        selectKnot: (id: number) => void;
        focusNewCommand: (id: number) => void;
    }

    let { knot, processResult, selectKnot, focusNewCommand }: Props = $props();

    let blessEnabled = $derived(knot.category != Category.OK);
    let blessClass = $derived(blessEnabled ? null : "text-gray-600 cursor-not-allowed");
    let editLabel;

    let knotColor = $derived(category2color.get(knot.category));
    let controlColor = $derived(category2color.get(knot.treeCategory));

    let actionDropdownOpen = $state(false);
    let childDropdownOpen = $state(false);

    async function post(payload: common.Payload): Promise<common.ActionResult> {
        actionDropdownOpen = false;

        let result = await common.postApi(payload);

        processResult(result);

        return result;
    }

    function bless() {
        if (blessEnabled) {
            post({ action: "bless", id: knot.id });
        }
    }

    function blessTo() {
        if (blessEnabled) {
            post({ action: "bless-to", id: knot.id });
        }
    }

    function replay() {
        post({ action: "replay", id: knot.id });
    }

    function setSelectedId(selectedId) {
        actionDropdownOpen = false;
        childDropdownOpen = false;
        selectKnot(selectedId);
    }

    function deleteKnot() {
        post({ action: "delete", id: knot.id });
    }

    // Select this node as the deepest selected node; which will cause the NewCommand to apply to this knot
    function newChild() {
        actionDropdownOpen = false;
        focusNewCommand(knot.id);
    }

    function startEditLabel() {
        actionDropdownOpen = false;
        editLabel.activate();
    }

    function onEditLabelComplete(e) {
        // TODO: make this work
        post({ action: "label", id: knot.id, label: e.detail });
    }
</script>

<div class="border-x-4 {knotColor.border}" id="knot_{knot.id}">
    <KnotText response={knot.data.response} unblessed={knot.data.unblessed}>
        <div
            slot="actions"
            class="whitespace-normal flex flex-row absolute top-2 right-2 gap-x-2"
        >
            {#if knot.data.label}
                <span class="text-bold bg-gray-200 border-1 p-1 rounded-md"
                    >{knot.data.label}</span
                >
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
                    >Replay
                    <Helper>Run from start to here</Helper>
                    </DropdownItem >
                {#if knot.id != 0}
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
                {#if knot.id != 0}
                    <DropdownItem
                        on:click={blessTo}
                        class="{blessClass} hover:bg-slate-200"
                    >
                        Bless To Here
                        <Helper>Bless all knots from root to here</Helper>
                    </DropdownItem>
                    <DropdownItem
                        on:click={startEditLabel}
                        class="hover:bg-slate-200"
                    >
                        Edit Label
                        <Helper>Change label for knot</Helper>
                    </DropdownItem>
                {/if}
                <DropdownItem on:click={newChild} class="hover:bg-slate-200">
                    New Child
                    <Helper>Add a new command after this</Helper>
                </DropdownItem>
            </Dropdown>
            {#if knot.children.length > 0}
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
                    {#each knot.children as child (child.id)}
                        <DropdownItem
                            onclick={() => setSelectedId(child.id)}
                            class={category2color.get(child.treeCategory)
                                .background}
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
    value={knot.data.label}
    bind:this={editLabel}
/>
