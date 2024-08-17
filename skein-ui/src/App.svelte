<script>
  import Knot from "./lib/Knot.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";
  import { load, postApi } from "./lib/common.js";
  import { Button } from "flowbite-svelte";
  import { Navbar, NavBrand, NavLi, NavUl, NavHamburger } from 'flowbite-svelte';

  let knots = writable(new Map());
  let childNames = writable(new Map());

  let loaded = false;

  let enableUndo = false;
  let enableRedo = false;

  setContext("knots", knots);
  setContext("childNames", childNames);

  function processResult(result) {
    knots.update((m) => {
      result.updates.forEach((n) => {
        let knot = m.get(n.id) || {};
        if (knot.node) {
          knot.node.set(n);
        } else {
          // A new node needs to be wrapped in a writable, added to the map
          knot.node = writable(n);
          knot.parentId = n.parent_id;
          m.set(n.id, knot);
        }

        if (!loaded) {
          knot.selectedId = n.children[0];
        }
      });

      // Note that for deleted nodes, we leave the knot around; deleted knots simply won't be
      // reachable anymore. We use that data here:

      result.removed_ids.forEach((id) => {
        let knot = m.get(id);
        let parentId = knot.parentId;

        // If a deleted knot was the selectedId of its parent knot, then clear the parent's
        // selectedId.
        let parentKnot = m.get(parentId);
        if (parentKnot?.selectedId == id) {
          parentKnot.selectedId = null;
        }
      });

      return m;
    });

    childNames.update((m) => {
      result.updates.forEach((n) => m.set(n.id, n.command));

      return m;
    });

    enableUndo = result.enable_undo;
    enableRedo = result.enable_redo;
  }

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
   
<Navbar  class="px-2 sm:px-4 py-2.5 fixed w-full z-20 top-0 start-0 border-b">
  <NavBrand>
    <span class="self-center whitespace-nowrap text-xl font-semibold dark:text-white">Dialog Skein</span>
  </NavBrand>
  <div class="flex md:order-2 space-x-2">
    <Button color="blue" size="xs" on:click={save}>Save</Button>
    <Button color="blue" size="xs" on:click={undo} disabled={!enableUndo}
      >Undo</Button
    >
    <Button color="blue" size="xs" on:click={redo} disabled={!enableRedo}
      >Redo</Button
    >
  <NavHamburger  />
</div>
</Navbar>

<div class="container mx-lg mx-auto px-8 py-4 mt-16">
  {#if loaded}
    <Knot id={0} on:result={(event) => processResult(event.detail)} />
  {/if}
</div>
</div>
