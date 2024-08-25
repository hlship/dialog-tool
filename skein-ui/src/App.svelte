<script>
  import Knot from "./lib/Knot.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";
  import { load, postApi, updateStoreMap } from "./lib/common.js";
  import { deriveDisplayIds, deriveKnotTotals } from "./lib/derived";
  import { Button, Navbar, NavBrand, NavHamburger } from "flowbite-svelte";
  import { UndoOutline, RedoOutline, FloppyDiskAltSolid} from "flowbite-svelte-icons";

  const globals = {
    // id -> node data (from service)
    knots: writable(new Map()),
    // id-> command (string)
    knotCommands: writable(new Map()),
    // id-> selected child id
    selected: writable(new Map()),
  };

  setContext("globals", globals);

  const knotTotals = deriveKnotTotals(globals.knots);

  let loaded = false;

  let enableUndo = false;
  let enableRedo = false;

  function processResult(result) {
    updateStoreMap(globals.knots, (_knots) => {
      updateStoreMap(globals.knotCommands, (_knotCommands) => {
        updateStoreMap(globals.selected, (_selected) => {
          result.updates.forEach((node) => {
            _knots.set(node.id, node);
            if (!loaded) {
              _selected.set(node.id, node.children[0]);
            }

            _knotCommands.set(node.id, node.command);
          });
          result.removed_ids.forEach((id) => {
            let parent_id = _knots.get(id)?.parent_id;

            if (_selected.get(parent_id) == id) {
              _selected.delete(parent_id);
            }
            _knots.delete(id);
          });
        });
      });
    });

    enableUndo = result.enable_undo;
    enableRedo = result.enable_redo;
  }

  let displayIds = deriveDisplayIds(globals.knots, globals.selected);

  onMount(async () => {
    let result = await load();

    processResult(result);

    loaded = true;
  });

  async function doPost(request) {
    let result = await postApi(request);

    processResult(result);
  }

  function save() {
    doPost({ action: "save" });
  }

  function undo() {
    doPost({ action: "undo" });
  }

  function redo() {
    doPost({ action: "redo" });
  }
</script>

<div class="relative px-8">
  <Navbar class="px-2 sm:px-4 py-2.5 fixed w-full z-20 top-0 start-0 border-b">
    <NavBrand>
      <span
        class="self-center whitespace-nowrap text-xl font-semibold dark:text-white"
        >Dialog Skein</span
      >
    </NavBrand>
    <div class="mx-0 inline-flex">
      <div class="text-black bg-green-400  p-2 font-semibold rounded-l-lg">
        {$knotTotals.ok}
      </div>
      <div class="text-black bg-yellow-200 p-2 font-semibold">
        {$knotTotals.unblessed}
      </div>
      <div class="text-black bg-red-500 p-2 font-semibold rounded-r-lg">
        {$knotTotals.error}
      </div>
    </div>
    <div class="flex md:order-2 space-x-2">
      <Button color="blue" size="xs" on:click={save}>
        <FloppyDiskAltSolid class="w-5 h-5 me-2"/> Save</Button>
      <Button color="blue" size="xs" on:click={undo} disabled={!enableUndo}>
        <UndoOutline class="w-5 h-5 me-2"/> Undo</Button
      >
      <Button color="blue" size="xs" on:click={redo} disabled={!enableRedo}>
        <RedoOutline class="w-5 h-5 me-2"/>Redo</Button >
      <NavHamburger />
    </div>
  </Navbar>

  <div class="container mx-lg mx-auto px-8 py-4 mt-20">
    {#if loaded}
      {#each $displayIds as knotId}
        <Knot id={knotId} on:result={(event) => processResult(event.detail)} />
      {/each}
    {/if}
  </div>
</div>
