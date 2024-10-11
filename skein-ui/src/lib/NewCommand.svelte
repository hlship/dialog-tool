<script>
    import { createEventDispatcher, tick, getContext } from "svelte";
    import { selectChild } from "./common.js";
    import { postApi } from "./common.js" ;
    import { Input, Label } from "flowbite-svelte";
    import { AngleRightOutline } from "flowbite-svelte-icons";
    const dispatcher = createEventDispatcher();

    const selected = getContext("selected");

    export let parentId;
    let newCommand = "";
    let inputElement;

    export function focus() {
      // See https://github.com/themesberg/flowbite-svelte/discussions/393
      // They think it's a A11y problem, but this is my hack workaround.
 
      let element = inputElement && inputElement.querySelector && inputElement.querySelector("input")[0];

      if (element) { element.focus(); }

    }


    async function runNewCommand(_event) {
        let priorParentId = parentId;

        const result = await postApi({
            action: "new-command",
            command: newCommand,
            id: parentId,
        });

        await dispatcher("result", result);

        selectChild(selected, priorParentId, result.new_id);

        newCommand = "";

        await tick();

        focus();

    }
</script>

<Label class="space-y-2">
    <Input type="text" placeholder="New command" size="lg" bind:this={inputElement}
    bind:value={newCommand}
    on:change={runNewCommand}>
        <AngleRightOutline slot="left" class="w-4 h-4" />
    </Input>
</Label>

