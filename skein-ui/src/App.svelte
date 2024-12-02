<script lang="ts">
  import Knot from "./lib/Knot.svelte";
  import NewCommand from "./lib/NewCommand.svelte";
  import ReplayAllModal from "./lib/ReplayAllModal.svelte";
  import ModalAlert from "./lib/ModalAlert.svelte";
  import { onMount, tick } from "svelte";
  import { load, category, postApi } from "./lib/common.svelte";
  import * as d from "./lib/derived.svelte";
  import { SvelteMap } from "svelte/reactivity";
  import { type KnotData, type KnotNode, Category } from "./lib/types";
  import { type ActionResult, type Payload } from "./lib/common.svelte";
  import {
    Button,
    Navbar,
    NavBrand,
    Dropdown,
    DropdownItem,
    Tooltip,
  } from "flowbite-svelte";
  import {
    UndoOutline,
    RedoOutline,
    FloppyDiskAltSolid,
    PlaySolid,
    ChevronDownOutline,
  } from "flowbite-svelte-icons";

  let knots = new SvelteMap<number, KnotData>();
  let id2selected = new SvelteMap<number, number>();

  let knotTotals = $derived(d.deriveKnotTotals(knots));
  let displayIds = $derived(d.deriveDisplayIds(knots, id2selected));

  let id2category = $derived(d.deriveKnotCategory(knots));

  // This is provided to the NewCommand component because any new command is created as a child of that.
  let lastSelectedKnotId = $derived(displayIds[displayIds.length - 1]);

  let labelItems = $derived(d.deriveLabels(knots));

  let title = $state("Dialog Skein");
  let loaded = $state(false);
  let enableUndo = $state(false);
  let enableRedo = $state(false);
  let alertMessage: string = $state(null);
  let modalAlertRunning = $state(false);

  function alert(message: string): void {
    alertMessage = message;
    modalAlertRunning = true;
  }

  function processResult(result: ActionResult): void {
    // The Svelte4 code did all the updates in a single block, to minimize
    // the number of derived calculations; trusting that Svelte5 is smart about this.
    for (const knot of result.updates) {
      knots.set(knot.id, knot);
      if (!loaded) {
        id2selected.set(knot.id, knot.children[0]);
      }
    }

    // When a node is deleted, check its parent node's selected child;
    // If they match, then delete the parent node's selected.

    for (const id of result.removed_ids) {
      let parent_id = knots.get(id)?.parent_id;

      if (id2selected.get(parent_id) == id) {
        id2selected.delete(parent_id);
      }
      knots.delete(id);
    }

    enableUndo = result.enable_undo;
    enableRedo = result.enable_redo;
  }

  function knotNode(id: number): KnotNode {
    const data = knots.get(id);

    return {
      id,
      data,
      category: category(data),
      treeCategory: id2category.get(id) || Category.OK,
      selectedChildId: id2selected.get(id),
      children: data.children.map((childId) => {
        const child = knots.get(childId);
        return {
          id: childId,
          label: child.command,
          treeCategory: id2category.get(childId) || Category.OK,
        };
      }),
    };
  }

  onMount(async () => {
    let result = await load();

    processResult(result);

    if (result.title) {
      title = result.title;
    }

    loaded = true;
  });

  async function postPayload(request: Payload): Promise<void> {
    let result = await postApi(request);

    processResult(result);
  }

  function save() {
    postPayload({ action: "save" });
  }

  function undo() {
    postPayload({ action: "undo" });
  }

  function redo() {
    postPayload({ action: "redo" });
  }

  let replayAllModal;

  function replayAll() {
    replayAllModal.run();
  }

  function selectKnot(id: number): void {
    let childId = id;
    while (childId != undefined) {
      const knot = knots.get(childId);
      const parentId = knot.parent_id;

      id2selected.set(parentId, childId);

      childId = parentId;
    }
  }

  async function jumpTo(knotId) {
    // Wasn't happy about this before, even less so w/ Svelte5.
    let elementId = `knot_${knotId}`;
    let element = document.getElementById(elementId);

    if (element == undefined) {
      selectKnot(knotId);

      while (element == undefined) {
        await tick();
        element = document.getElementById(elementId);
      }
    }

    // TODO: If the element has no children, then scroll to the command input field instead.
    element.scrollIntoView({ behavior: "smooth", block: "end" });
  }

  let newCommand;

  function focusNewCommand(id: number) {
    // id should be visible, this truncates the display list to that id
    // such that the new command will be a child of the id.
    id2selected.delete(id);

    newCommand.focus();
  }
</script>

<div class="relative px-8">
  <Navbar
    id="navBar"
    class="px-2 sm:px-4 py-2.5 fixed w-full z-20 top-0 start-0 border-b"
  >
    <NavBrand>
      <span
        class="self-center whitespace-nowrap text-xl font-semibold dark:text-white"
        >{title}</span
      >
    </NavBrand>
    <div class="mx-0 inline-flex">
      <div class="text-black bg-green-400 p-2 font-semibold rounded-l-lg">
        {knotTotals.get(Category.OK)}
      </div>
      <div class="text-black bg-yellow-200 p-2 font-semibold">
        {knotTotals.get(Category.NEW)}
      </div>
      <div class="text-black bg-red-500 p-2 font-semibold rounded-r-lg">
        {knotTotals.get(Category.ERROR)}
      </div>
      <Button class="ml-8" color="blue" size="xs"
        >Jump <ChevronDownOutline /></Button
      >
      <Dropdown class="overflow-y-auto h-96">
        {#each labelItems as item}
          <DropdownItem on:click={() => jumpTo(item.id)}
            >{item.label}</DropdownItem
          >
        {/each}
      </Dropdown>
    </div>
    <div class="flex md:order-2 space-x-2">
      <Button color="blue" size="xs" on:click={replayAll}>
        <PlaySolid class="w-5 h-5 me-2" />Replay All</Button
      >
      <Tooltip>Replay <em>every</em> knot</Tooltip>
      <Button color="blue" size="xs" on:click={save}>
        <FloppyDiskAltSolid class="w-5 h-5 me-2" /> Save</Button
      >
      <Button color="blue" size="xs" on:click={undo} disabled={!enableUndo}>
        <UndoOutline class="w-5 h-5 me-2" /> Undo</Button
      >
      <Button color="blue" size="xs" on:click={redo} disabled={!enableRedo}>
        <RedoOutline class="w-5 h-5 me-2" />Redo</Button
      >
    </div>
  </Navbar>

  <div class="container mx-lg mx-auto mt-16">
    {#if loaded}
      {#each displayIds as knotId}
        <Knot
          knot={knotNode(knotId)}
          {processResult}
          {selectKnot}
          {focusNewCommand}
          {alert}
        />
      {/each}
    {/if}

    <NewCommand
      {processResult}
      {selectKnot}
      parentId={lastSelectedKnotId}
      bind:this={newCommand}
    />
  </div>
</div>

<ReplayAllModal {knots} {processResult} bind:this={replayAllModal} />
<ModalAlert message={alertMessage} bind:running={modalAlertRunning} />
