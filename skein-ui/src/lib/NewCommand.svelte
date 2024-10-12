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
 
      // But this hack works, knowing that there's only one such element.

     document.querySelector(".x-new-command-input")?.focus();
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
    <Input type="text" size="lg" bind:this={inputElement} class="x-new-command-input"
    bind:value={newCommand}
    on:change={runNewCommand}>
        <AngleRightOutline slot="left" class="w-4 h-4" />
    </Input>
</Label>

