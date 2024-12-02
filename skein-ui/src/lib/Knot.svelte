<script lang="ts">
    import { category2color } from "./knot-color";
    import { postApi, type ActionResult, type Payload } from "./common.svelte";
    import { Button, Dropdown, DropdownItem, Helper } from "flowbite-svelte";
    import KnotText from "./KnotText.svelte";
    import EditProperty from "./EditProperty.svelte";
    import { DotsVerticalOutline, CodeMergeSolid } from "flowbite-svelte-icons";
    import { Category, type KnotNode } from "./types";

    interface Props {
        knot: KnotNode,
        processResult: (result: ActionResult) => void,
        selectKnot: (id: number) => void,
        focusNewCommand: (id: number) => void,
        alert: (message:string) => void
    }

    let { knot, processResult, selectKnot, focusNewCommand, alert }: Props = $props();

    let blessEnabled = $derived(knot.category != Category.OK);
    let blessClass = $derived(blessEnabled ? null : "text-gray-600 cursor-not-allowed");
    let editLabel, editCommand, insertParent;

    let knotColor = $derived(category2color.get(knot.category));
    let controlColor = $derived(category2color.get(knot.treeCategory));

    let actionDropdownOpen = $state(false);
    let childDropdownOpen = $state(false);
    let error : string = $state(null);

    async function post(payload: Payload): Promise<ActionResult> {
        actionDropdownOpen = false;

        let result = await postApi(payload);

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

    async function onEditLabel(newLabel : string) {
        post({ action: "label", id: knot.id, label: newLabel });

        return true;
    }

function activateField(field) {
    actionDropdownOpen = false;
    error = null;
    field.activate()   ; 
}

async function completeEditProperty(payload : Payload) : Promise<boolean> {
    const result = await post(payload);

    if (result.error) {
        error = result.error;
        return false;
    }

    return true;
}

async function spliceOutKnot() {
    const result = await post({action: "splice-out", id: knot.id});

    processResult(result);

    if (result.error) {
        alert(result.error)
    }

}

async function onEditCommand(newCommand: string) {
   return  await completeEditProperty({ action: "edit-command", id: knot.id, command: newCommand});
 }

 async function onInsertParent(newCommand: string) {
    const result =   await post({ action: "insert-parent", id: knot.id, command: newCommand});

if (result.error) {
    error = result.error;
    return false;
}

    // This should recalculate the displayed knots to include the new parent
    selectKnot(knot.id);

return true;
 }


const ddcolor = "hover:bg-slate-200";

</script>

<div class="border-x-4 {knotColor.border}" id="knot_{knot.id}">
    <KnotText response={knot.data.response} unblessed={knot.data.unblessed}>
        {#snippet actions()}
        <div class="whitespace-normal flex flex-row absolute top-2 right-2 gap-x-2">
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
                class="w-96 bg-slate-100" >
                <DropdownItem onclick={replay} class={ddcolor}
                    >Replay
                    <Helper>Run from start to here</Helper>
                    </DropdownItem >
                {#if knot.id != 0}
                    <DropdownItem
                        onclick={deleteKnot}
                        class={ddcolor}>
                        Delete
                        <Helper>Delete this knot and all children</Helper>
                    </DropdownItem>
                    <DropdownItem
                    onclick={spliceOutKnot}
                    class={ddcolor}>
                    Splice Out
                    <Helper>Delete this knot, reparent childen up</Helper>
                </DropdownItem>
                {/if}
                <DropdownItem
                    onclick={bless}
                    class="{blessClass} {ddcolor}">
                    Bless
                    <Helper>Accept changes</Helper>
                </DropdownItem>
                {#if knot.id != 0}
                    <DropdownItem
                        onclick={blessTo}
                        class="{blessClass} {ddcolor}"
                    >
                        Bless To Here
                        <Helper>Bless all knots from root to here</Helper>
                    </DropdownItem>
                    <DropdownItem
                        onclick={() => activateField(editLabel)}
                        class={ddcolor}>
                        Edit Label
                        <Helper>Change label for knot</Helper>
                    </DropdownItem>
                    <DropdownItem onclick={() => activateField(editCommand)} class={ddcolor}>
                        Edit Command
                        <Helper>Change the command</Helper>
                    </DropdownItem>
                {/if}
                <DropdownItem onclick={newChild} class={ddcolor}>
                    New Child
                    <Helper>Add a new command after this</Helper>
                </DropdownItem>
                <DropdownItem onclick={() => activateField(insertParent)} class={ddcolor}>
                    Insert Parent
                    <Helper>Insert a command before this</Helper>
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
        {/snippet}
    </KnotText>
    <hr />
</div>

<EditProperty
    title="Edit Label"
    change={onEditLabel}
    value={knot.data.label}
    bind:this={editLabel}
/>

<EditProperty
title="Edit Command"
change={onEditCommand}
value={knot.data.command}
error={error}
bind:this={editCommand}
help="After renaming, the Skein will replay to this knot"> 
</EditProperty>

<EditProperty
title="Insert Parent"
change={onInsertParent}
value="z"
error={error}
bind:this={insertParent}
help="New command will be inserted between this command and its parent"> 
</EditProperty>
