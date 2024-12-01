<script lang="ts">
    import { tick } from "svelte";
    import { postApi, type ActionResult } from "./common.svelte";
    import { Input, Label } from "flowbite-svelte";
    import { AngleRightOutline } from "flowbite-svelte-icons";

    interface Props {
        parentId: number;
        processResult: (result: ActionResult) => void;
        selectKnot: (id: number) => void;
    }

    let { parentId, processResult, selectKnot }: Props = $props();

    let newCommand = $state("");

    export function focus() {
        // See https://github.com/themesberg/flowbite-svelte/discussions/393
        // They think it's a A11y problem, but this is my hack workaround.

        // But this hack works, knowing that there's only one such element.

        let element = document.querySelector(".x-new-command-input");

        // TODO: Typescripty way to ensure is HTMLElement
        if (element as HTMLElement) {
            element.focus();
            element.scrollIntoView({ behavior: "smooth", block: "center" });
        }
    }

    async function runNewCommand(_event) {
        let priorParentId = parentId;

        const result = await postApi({
            action: "new-command",
            command: newCommand,
            id: parentId,
        });

        processResult(result);
        selectKnot(result.new_id);

        newCommand = "";

        await tick();

        focus();
    }
</script>

<Label class="space-y-2">
    <Input
        type="text"
        size="lg"
        class="x-new-command-input"
        bind:value={newCommand}
        on:change={runNewCommand}
    >
        <AngleRightOutline slot="left" class="w-4 h-4" />
    </Input>
</Label>
